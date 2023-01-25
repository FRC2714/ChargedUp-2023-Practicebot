// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.revrobotics.AbsoluteEncoder;
import com.revrobotics.CANSparkMax;
import com.revrobotics.CANSparkMaxLowLevel;
import com.revrobotics.SparkMaxAbsoluteEncoder.Type;

import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.ProfiledPIDSubsystem;
import frc.robot.Constants.ArmConstants;

public class BaseJoint extends ProfiledPIDSubsystem {
  private CANSparkMax RightBaseMotor;
  private CANSparkMax LeftBaseMotor;

  private AbsoluteEncoder BaseEncoder;
  //ticks per rev: 8192
  //need to convert
  private double conversionFactor = 1/8192;
  /** Creates a new BaseJoint. */
  public BaseJoint() {
    super(
        // The ProfiledPIDController used by the subsystem
        new ProfiledPIDController(
            ArmConstants.kBaseJointP,
            0,
            0,
            // The motion profile constraints
            new TrapezoidProfile.Constraints(ArmConstants.kBaseJointMaxVelocity, ArmConstants.kBaseJointMaxAcceleration))
    );
    LeftBaseMotor = new CANSparkMax(ArmConstants.kLeftBaseJointMotorCanId, CANSparkMaxLowLevel.MotorType.kBrushless);
    RightBaseMotor = new CANSparkMax(ArmConstants.kRightBaseJointMotorCanId, CANSparkMaxLowLevel.MotorType.kBrushless);
    BaseEncoder = RightBaseMotor.getAbsoluteEncoder(Type.kDutyCycle);

    LeftBaseMotor.follow(RightBaseMotor, false);

  }

  public double getAngle() {
    return BaseEncoder.getPosition() * conversionFactor;
  }

  @Override
  public void useOutput(double output, TrapezoidProfile.State setpoint) {
    // Use the output (and optionally the setpoint) here
    //baseMotor1.setVoltage(output);
  }

  @Override
  public void periodic() {
    SmartDashboard.putNumber("Base Joint Encoder", BaseEncoder.getPosition());
    SmartDashboard.putNumber("Base Joint Angle", getAngle());
  }

  @Override
  public double getMeasurement() {
    // Return the process variable measurement here
    return getAngle();
  }
}
