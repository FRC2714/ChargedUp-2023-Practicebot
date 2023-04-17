// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.JoystickButton;
import edu.wpi.first.wpilibj2.command.button.Trigger;
import frc.robot.Constants.LEDConstants;
import frc.robot.Constants.LimelightConstants;
import frc.robot.Constants.OIConstants;
import frc.robot.Constants.ShooterConstants;
import frc.robot.commands.AutoBalance;
import frc.robot.commands.auto.NothingAuto;
import frc.robot.commands.auto.PathTestAuto;
import frc.robot.commands.auto.MIDDLE.OneConeBalanceMiddleAuto;
import frc.robot.commands.auto.MIDDLE.OneConeBalanceMobilityMiddleAuto;
import frc.robot.commands.auto.MIDDLE.TwoCargoBalanceMiddleAuto;
import frc.robot.commands.auto.OPEN.ThreeCargoOpenAuto;
import frc.robot.commands.auto.OPEN.TwoCargoBalanceOpenAuto;
import frc.robot.commands.auto.OPEN.TwoCargoOpenAuto;
import frc.robot.commands.auto.TERRAIN.TwoCargoTerrainAuto;
import frc.robot.subsystems.Infrastructure;
import frc.robot.subsystems.LED;
import frc.robot.subsystems.Limelight;
import frc.robot.subsystems.Superstructure;
import frc.robot.subsystems.Arm.Arm;
import frc.robot.subsystems.Arm.Claw;
import frc.robot.subsystems.Drive.DriveSubsystem;
import frc.robot.subsystems.Shooter.Shooter;
import frc.robot.subsystems.Superstructure.BUTTON;
import frc.robot.subsystems.Superstructure.CargoType;
import frc.robot.subsystems.Superstructure.DPAD;
import frc.robot.subsystems.Superstructure.ScoreMode;

/*
 * This class is where the bulk of the robot should be declared.  Since Command-based is a
 * "declarative" paradigm, very little robot logic should actually be handled in the {@link Robot}
 * periodic methods (other than the scheduler calls).  Instead, the structure of the robot
 * (including subsystems, commands, and button mappings) should be declared here.
 */

public class RobotContainer {
	// The robot's subsystems
	private final Infrastructure m_infrastructure = new Infrastructure();
	private final DriveSubsystem m_robotDrive = new DriveSubsystem();
	private final Limelight m_backLimelight = new Limelight("limelight-back", LimelightConstants.kBackLimelightPose);
	private final Arm m_arm = new Arm();
	private final Claw m_claw = new Claw();
	private final LED m_armLED = new LED(LEDConstants.kArmBlinkinPort);
	private final Shooter m_shooter = new Shooter();
	
	private final Superstructure m_superstructure = new Superstructure(m_robotDrive, m_arm, m_claw, m_shooter, m_backLimelight, m_armLED);

	// The driver's controller
	CommandXboxController m_driverController = new CommandXboxController(OIConstants.kDriverControllerPort);
	CommandXboxController m_operatorController = new CommandXboxController(OIConstants.kOperatorControllerPort);

	/**
	 * The container for the robot. Contains subsystems, OI devices, and commands.
	 */

	public RobotContainer() {
		// CommandScheduler.getInstance().registerSubsystem(m_infrastructure);
		// CommandScheduler.getInstance().registerSubsystem(m_robotDrive);
		// CommandScheduler.getInstance().registerSubsystem(m_backLimelight);
		// CommandScheduler.getInstance().registerSubsystem(m_shooter);
		// CommandScheduler.getInstance().registerSubsystem(m_claw);
		// CommandScheduler.getInstance().registerSubsystem(m_armLED);

		// Configure the button bindings
		configureButtonBindings();

		// Configure default commands
		m_robotDrive.setDefaultCommand(
			// The left stick controls translation of the robot.
			// Turning is controlled by the X axis of the right stick.
			new RunCommand(
				() -> m_robotDrive.drive(
					-MathUtil.applyDeadband(m_driverController.getLeftY(), OIConstants.kDriveDeadband),
					-MathUtil.applyDeadband(m_driverController.getLeftX(), OIConstants.kDriveDeadband),
					-0.8*MathUtil.applyDeadband(m_driverController.getRightX(), OIConstants.kDriveDeadband),
					true, false),
				m_robotDrive));

		m_infrastructure.enableCompressor();
	}

	public void setTeleopDefaultStates() {
		System.out.println("setTeleopDefaultStates()");
		new SequentialCommandGroup(
			m_superstructure.setCargoTypeCommand(CargoType.CONE),
			m_superstructure.setSubsystemState(DPAD.DOWN),
			m_backLimelight.setLEDCommand(false),
			new InstantCommand(() -> m_claw.close()),
			new InstantCommand(() -> m_robotDrive.resetModules())
		).schedule();
		}

	public void setAutoDefaultStates() {
		Commands.runOnce(m_robotDrive::zeroHeading, m_robotDrive).schedule();
		m_backLimelight.setLEDCommand(false).schedule();
	}

	public void updateTelemetry() {
		m_superstructure.periodic();
	}

	/**
	 * Use this method to define your button->command mappings. Buttons can be
	 * created by
	 * instantiating a {@link edu.wpi.first.wpilibj.GenericHID} or one of its
	 * subclasses ({@link
	 * edu.wpi.first.wpilibj.Joystick} or {@link XboxController}), and then calling
	 * passing it to a
	 * {@link JoystickButton}.
	 */

	private void configureButtonBindings() {
		DriverStation.silenceJoystickConnectionWarning(true);

		/////////////////////////////DRIVER CONTROLS/////////////////////////////////////////////////////////////
			
		//autoalign on right bumper
		m_driverController.rightBumper()
			.whileTrue(m_superstructure.getAlign());
		
		//hold to score on left bumper
		m_driverController.leftBumper()
			.onTrue(m_superstructure.ScoreCommand())
			.onFalse(m_shooter.stopCommand());

		//intake on right trigger while held 
		new Trigger(() -> m_driverController.getRightTriggerAxis() > 0.25)
			.onTrue(m_superstructure.shooterIntakeSequence()) //intake sequence
			.onFalse(m_superstructure.setSubsystemState(DPAD.UP)); //shooter to hold

		//outtake on left trigger while held
		new Trigger(() -> m_driverController.getLeftTriggerAxis() > 0.25)
			.onTrue(m_superstructure.shooterOuttakeSequence())
			.onFalse(m_superstructure.setSubsystemState(DPAD.UP).alongWith(m_shooter.stopCommand()));

		//release cube on y
		m_driverController.y()
			.onTrue(m_shooter.kickerOuttakeCommand(ShooterConstants.kKickSpeed))
			.onFalse(m_shooter.stopCommand());

		// m_driverController.b()
		// 	.onTrue(new InstantCommand(() -> m_shooter.setTunable()));

		//toggle claw intake on X
		m_driverController.x()
			.onTrue(m_claw.intakeCone());

		//reset gyro on back
		m_driverController.back()
			.onTrue(Commands.runOnce(m_robotDrive::zeroHeading, m_robotDrive));

		//lock wheels on right
		m_driverController.povRight()
			.whileTrue(new RunCommand(
            	() -> m_robotDrive.setX(),
            	m_robotDrive));

		//AutoBalance on left
		m_driverController.povLeft()
			.whileTrue(new AutoBalance(m_robotDrive, false));

		m_driverController.povDown() 
			.whileTrue(new AutoBalance(m_robotDrive, true));

		/////////////////////////////OPERATOR CONTROLS/////////////////////////////////////////////////////////////

		//set arm on start
		m_operatorController.start()
			.onTrue(m_superstructure.setScoreModeCommand(ScoreMode.ARM));

		//set shooter on back
		m_operatorController.back()
			.onTrue(m_superstructure.setScoreModeCommand(ScoreMode.SHOOTER));

		//TUCK on up
		m_operatorController.povUp()
			.onTrue(m_superstructure.setSubsystemState(DPAD.UP));
		//TRANSFER on down
		m_operatorController.povDown()
			.onTrue(m_superstructure.setSubsystemState(DPAD.DOWN));
		//FRONT on left
		m_operatorController.povLeft()
			.onTrue(m_superstructure.setSubsystemState(DPAD.LEFT));
		//BACK on right
		m_operatorController.povRight()
			.onTrue(m_superstructure.setSubsystemState(DPAD.RIGHT));

		//HIGH on Y
		m_operatorController.y()
			.onTrue(m_superstructure.setScoreLevelCommand(BUTTON.Y));
		//MIDDLE on B
		m_operatorController.b()
			.onTrue(m_superstructure.setScoreLevelCommand(BUTTON.B));
		//INTAKE on A
		m_operatorController.a()
			.onTrue(m_superstructure.setScoreLevelCommand(BUTTON.A));
		//LOW on X
		m_operatorController.x()
			.onTrue(m_superstructure.setScoreLevelCommand(BUTTON.X));
		
		// cone mode on right bumper
		m_operatorController.rightBumper()
			.onTrue(m_superstructure.setCargoTypeCommand(CargoType.CONE));

		// cube mode on left bumper
		m_operatorController.leftBumper()
			.onTrue(m_superstructure.setCargoTypeCommand(CargoType.CUBE));
	}

	public Command getNothingAuto() {
		return new NothingAuto();
	}

	public Command getPathTestAuto() {
		return new PathTestAuto(m_robotDrive);
	}

	public Command getOneConeBalanceMiddleAuto() {
		return new OneConeBalanceMiddleAuto(m_robotDrive, m_superstructure);
	}

	public Command getOneConeBalanceMobilityMiddleAuto() {
		return new OneConeBalanceMobilityMiddleAuto(m_robotDrive, m_superstructure);
	}

	public Command getTwoCargoBalanceMiddleAuto() {
		return new TwoCargoBalanceMiddleAuto(m_robotDrive, m_superstructure, m_shooter);
	}

	public Command getTwoCargoOpenAuto() {
		return new TwoCargoOpenAuto(m_robotDrive, m_superstructure, m_shooter);
	}

	public Command getTwoCargoBalanceOpenAuto() {
		return new TwoCargoBalanceOpenAuto(m_robotDrive, m_superstructure, m_shooter);
	}

	public Command getThreeCargoOpenAuto() {
		return new ThreeCargoOpenAuto(m_robotDrive, m_superstructure, m_shooter);
	}

	public Command getTwoCargoTerrainAuto() {
		return new TwoCargoTerrainAuto(m_robotDrive, m_superstructure, m_shooter);
	}
}
