// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems.Shooter;

import com.revrobotics.AbsoluteEncoder;
import com.revrobotics.CANSparkMax;
import com.revrobotics.CANSparkMaxLowLevel;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.CANSparkMax.IdleMode;
import com.revrobotics.SparkMaxAbsoluteEncoder.Type;

import edu.wpi.first.math.controller.ArmFeedforward;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.ParallelCommandGroup;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj2.command.WaitUntilCommand;
import frc.robot.Constants.ShooterConstants;
import frc.robot.subsystems.Limelight;
import frc.robot.utils.InterpolatingTreeMap;

public class Shooter extends SubsystemBase {
  private Limelight m_frontLimelight;

  private CANSparkMax kickerMotor;
  private CANSparkMax pivotMotor;

  private CANSparkMax topFlywheelMotor;
  private CANSparkMax bottomFlywheelMotor;

  private RelativeEncoder flywheelEncoder;

  private AbsoluteEncoder pivotEncoder;

  private InterpolatingTreeMap velocityMap = new InterpolatingTreeMap();
  private InterpolatingTreeMap pivotMap = new InterpolatingTreeMap();

  private PIDController pivotController = new PIDController(0, 0, 0);
  private PIDController flywheelController = new PIDController(0.5, 0, 0);

  //private ArmFeedforward pivotFeedforward = new ArmFeedforward(0, 0.49, 0.97, 0.01);

  public enum ShooterState {
    INTAKING, OUTTAKING, STOPPED
  }

  private static ShooterState shooterState = ShooterState.STOPPED;

  private Timer shooterRunningTimer = new Timer();

  private boolean isShooterEnabled = false;
  private boolean isDynamicEnabled = false;
  

  /** Creates a new Shooter. */
  public Shooter(Limelight m_frontLimelight) {
    this.m_frontLimelight = m_frontLimelight;
    
    kickerMotor = new CANSparkMax(ShooterConstants.kKickerMotorCanId, CANSparkMaxLowLevel.MotorType.kBrushless);
    pivotMotor = new CANSparkMax(ShooterConstants.kPivotMotorCanId, CANSparkMaxLowLevel.MotorType.kBrushless);
    kickerMotor.setInverted(true);
    pivotMotor.setInverted(false);

    kickerMotor.setIdleMode(IdleMode.kBrake);
    pivotMotor.setIdleMode(IdleMode.kBrake);
    kickerMotor.setSmartCurrentLimit(ShooterConstants.kKickerMotorCurrentLimit);
    pivotMotor.setSmartCurrentLimit(ShooterConstants.kPivotMotorCurrentLimit);

    kickerMotor.enableVoltageCompensation(ShooterConstants.kNominalVoltage);
    pivotMotor.enableVoltageCompensation(ShooterConstants.kNominalVoltage);

    pivotEncoder = pivotMotor.getAbsoluteEncoder(Type.kDutyCycle);
    pivotEncoder.setPositionConversionFactor(ShooterConstants.kPivotPositionConversionFactor);
    pivotEncoder.setInverted(true);
    pivotEncoder.setZeroOffset(200);

    topFlywheelMotor = new CANSparkMax(ShooterConstants.kTopFlywheelMotorCanId, CANSparkMaxLowLevel.MotorType.kBrushless);
    bottomFlywheelMotor = new CANSparkMax(ShooterConstants.kBottomFlywheelMotorCanId, CANSparkMaxLowLevel.MotorType.kBrushless);
    bottomFlywheelMotor.follow(topFlywheelMotor, true);
    topFlywheelMotor.setInverted(false);

    topFlywheelMotor.setIdleMode(IdleMode.kCoast);
    bottomFlywheelMotor.setIdleMode(IdleMode.kCoast);

    flywheelEncoder = topFlywheelMotor.getEncoder();
    pivotEncoder.setVelocityConversionFactor(2*Math.PI*1.0);//gear ratio

    pivotController.disableContinuousInput();
    pivotController.setTolerance(Units.degreesToRadians(4));

    populateVelocityMap();
    populatePivotMap();
  }

  //Populate Maps
  private void populateVelocityMap() {
    velocityMap.put(0.0, 0.0);
    velocityMap.put(5.0, 1000.0);
  }

  private void populatePivotMap() {
    pivotMap.put(0.0, 0.0);
    pivotMap.put(10.0, 120.0);
  }

  //enable funtions
  public void setShooterEnabled(boolean isShooterEnabled) {
    this.isShooterEnabled = isShooterEnabled;
  }

  public void setDynamicEnabled(boolean isDynamicEnabled) {
    this.isDynamicEnabled = isDynamicEnabled;
  }

  public InstantCommand setDynamicEnabledCommand(boolean isDynamicEnabled) {
    return new InstantCommand(() -> setDynamicEnabled(isDynamicEnabled));
  }

  //PIVOT
  public double getPivotAngleRadians() {
    return pivotEncoder.getPosition() / ShooterConstants.kPivotGearRatio - Units.degreesToRadians(37 + 86);
  }

  public void setTargetPivot(double targetAngleDegrees) {
    if (pivotController.getP() == 0) { pivotController.setP(0.2);} //prevent jumping on enable
    pivotController.setSetpoint(Units.degreesToRadians(targetAngleDegrees));
  }

  public double getPivotTarget() {
    return pivotController.getSetpoint();
  }

  public boolean atPivotSetpoint() {
    return pivotController.atSetpoint();
  }

  public void setCalculatedPivotVoltage() {
    if (isShooterEnabled) {
      pivotMotor.setVoltage((pivotController.calculate(getPivotAngleRadians() * ShooterConstants.kNominalVoltage)));
    } else {
      pivotMotor.setVoltage(0);
    }
  }

  //FLYWHEEL
  public double getFlywheelVelocity() {
    return flywheelEncoder.getVelocity() / 1.0;//gear ratio
  }

  public void setTargetVelocity(double targetRPM) {
    flywheelController.setSetpoint(Units.rotationsPerMinuteToRadiansPerSecond(targetRPM));
  }

  public double getVelocityTarget() {
    return flywheelController.getSetpoint();
  }

  public boolean atVelocitySetpoint() {
    return flywheelController.atSetpoint();
  }

  private void setCalculatedFlywheelVoltage() {
    if (isShooterEnabled) {
      topFlywheelMotor.setVoltage(flywheelController.calculate(getFlywheelVelocity()));
    } else {
      topFlywheelMotor.setVoltage(0);
    }
  }

  //Dynamic
  private double getDynamicPivot() {
    return m_frontLimelight.isTargetVisible()
        ? pivotMap.getInterpolated(Units.metersToFeet(m_frontLimelight.getDistanceToGoalMeters()))
        : ShooterConstants.kPivotHoldAngleDegrees;
  }

  private double getDynamicVelocity() {
    return m_frontLimelight.isTargetVisible()
        ? velocityMap.getInterpolated(Units.metersToFeet(m_frontLimelight.getDistanceToGoalMeters()) + 0)
        : 0;
  }

  public void setDynamicShooter() {
    if(isDynamicEnabled) {
      setTargetVelocity(getDynamicVelocity());
      setTargetPivot(getDynamicPivot());
    }
  }

  private void intake() {
    setTargetVelocity(100);
    //topFlywheelMotor.set(0.4);
    kickerMotor.setVoltage(ShooterConstants.kIntakeMotorSpeed*ShooterConstants.kNominalVoltage);
    if (shooterState != ShooterState.INTAKING) {
      shooterRunningTimer.reset();
      shooterRunningTimer.start();
      shooterState = ShooterState.INTAKING;
    }
  }

  private void outtake(double power) {
    setTargetVelocity(-100);
    //topFlywheelMotor.set(-0.4);
    kickerMotor.setVoltage(-power*ShooterConstants.kNominalVoltage);
    if (shooterState != ShooterState.OUTTAKING) {
      shooterRunningTimer.reset();
      shooterRunningTimer.start();
      shooterState = ShooterState.OUTTAKING;
    }
  }

  public void stop() {
    //setFlywheelTargetVelocity(0);
    //topFlywheelMotor.set(0);
    setTargetVelocity(0);
    kickerMotor.set(0);
  }

  public Command intakeCommand() {
    return new InstantCommand(() -> intake());
  }

  public Command outtakeCommand() {
    return new InstantCommand(() -> outtake(ShooterConstants.kOuttakeMotorSpeed));
  }

  public Command stopCommand() {
    return new InstantCommand(() -> stop());
  }

  public Command pivotToIntake() {
    return new SequentialCommandGroup(
      new InstantCommand(() -> setTargetPivot(ShooterConstants.kPivotIntakeAngleDegrees)),
      new WaitUntilCommand(() -> atPivotSetpoint()));
  }

  public Command pivotToOuttake() {
    return new SequentialCommandGroup(
      new InstantCommand(() -> setTargetPivot(ShooterConstants.kPivotOuttakeAngleDegrees)),
      new WaitUntilCommand(() -> atPivotSetpoint()));
  }

  public Command pivotToRetract() {
    return new SequentialCommandGroup(
      new InstantCommand(() -> setTargetPivot(ShooterConstants.kPivotRetractAngleDegrees)),
      new WaitUntilCommand(() -> atPivotSetpoint()));
  }

  public Command pivotToHold() {
    flywheelController.setSetpoint(0);
    return new SequentialCommandGroup(
      new InstantCommand(() -> setTargetPivot(ShooterConstants.kPivotHoldAngleDegrees)),
      new WaitUntilCommand(() -> atPivotSetpoint()));
  }

  public Command pivotToShoot() {
    return new SequentialCommandGroup(
      new InstantCommand(() -> setTargetPivot(ShooterConstants.kPivotShootAngleDegrees)),
      new WaitUntilCommand(() -> atPivotSetpoint()));
  }

  public Command intakeSequence() {
    return new SequentialCommandGroup(
      intakeCommand(),
      pivotToIntake());
  }

  public Command outtakeSequence() {
    return new SequentialCommandGroup(
      pivotToOuttake(),
      outtakeCommand());
  }

  public boolean isCurrentSpikeDetected() {
    return (shooterRunningTimer.get() > 0.15) && //excludes current spike when motor first starts
      (kickerMotor.getOutputCurrent() > 25) && //cube intake current threshold
      (shooterState == ShooterState.INTAKING);
  }

  public boolean isCubeDetected() {
    return isCurrentSpikeDetected() 
      && (pivotController.getSetpoint() != Units.degreesToRadians(ShooterConstants.kPivotHoldAngleDegrees)) 
      && (flywheelController.getSetpoint() != 0);
  }

  @Override
  public void periodic() {
    // This method will be called once per scheduler run
    setDynamicShooter();
    setCalculatedPivotVoltage();
    setCalculatedFlywheelVoltage();
    
    SmartDashboard.putNumber("Shooter Pivot", Units.radiansToDegrees(getPivotAngleRadians()));

    // SmartDashboard.putNumber("Flywheel RPM", Units.radiansPerSecondToRotationsPerMinute(getFlywheelVelocity()));
    // SmartDashboard.putNumber("interpolated velocity", getDynamicFlywheelVelocity());
    // SmartDashboard.putNumber("interpolated pivot", getDynamicPivot());
    // SmartDashboard.putNumber("front distance from goal", Units.metersToFeet(m_frontLimelight.getDistanceToGoalMeters()));
    // SmartDashboard.putBoolean("target visible", m_frontLimelight.isTargetVisible());
  }
}
