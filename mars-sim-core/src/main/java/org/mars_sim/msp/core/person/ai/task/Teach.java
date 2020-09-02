/**
 * Mars Simulation Project
 * Teach.java
 * @version 3.1.2 2020-09-02
 * @author Scott Davis
 */
package org.mars_sim.msp.core.person.ai.task;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mars_sim.msp.core.LogConsolidated;
import org.mars_sim.msp.core.Msg;
import org.mars_sim.msp.core.Simulation;
import org.mars_sim.msp.core.Unit;
import org.mars_sim.msp.core.person.Person;
import org.mars_sim.msp.core.person.PhysicalCondition;
import org.mars_sim.msp.core.person.ai.NaturalAttributeManager;
import org.mars_sim.msp.core.person.ai.NaturalAttributeType;
import org.mars_sim.msp.core.person.ai.SkillType;
import org.mars_sim.msp.core.person.ai.social.Relationship;
import org.mars_sim.msp.core.person.ai.social.RelationshipManager;
import org.mars_sim.msp.core.person.ai.task.utils.Task;
import org.mars_sim.msp.core.person.ai.task.utils.TaskPhase;
import org.mars_sim.msp.core.robot.Robot;
import org.mars_sim.msp.core.structure.building.Building;
import org.mars_sim.msp.core.structure.building.BuildingManager;
import org.mars_sim.msp.core.structure.building.function.FunctionType;
import org.mars_sim.msp.core.structure.building.function.LifeSupport;
import org.mars_sim.msp.core.tool.RandomUtil;
import org.mars_sim.msp.core.vehicle.Crewable;
import org.mars_sim.msp.core.vehicle.Rover;

/**
 * This is a task for teaching a student a task.
 */
public class Teach extends Task implements Serializable {

	/** default serial id. */
	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(Teach.class.getName());
	private static String loggerName = logger.getName();
	private static String sourceName = loggerName.substring(loggerName.lastIndexOf(".") + 1, loggerName.length());
	
	/** Task name */
	private static final String NAME = Msg.getString("Task.description.teach"); //$NON-NLS-1$

	/** Task phases. */
	private static final TaskPhase TEACHING = new TaskPhase(Msg.getString("Task.phase.teaching")); //$NON-NLS-1$

	// Static members
	/** The stress modified per millisol. */
	private static final double STRESS_MODIFIER = -.1D;

	/**
	 * The improvement in relationship opinion of the teacher from the student per
	 * millisol.
	 */
	private static final double BASE_RELATIONSHIP_MODIFIER = .2D;

	// Data members
	private Person student;
	private Task teachingTask;

	/**
	 * Constructor.
	 * 
	 * @param unit the unit performing the task.
	 */
	public Teach(Unit unit) {
		super(NAME, unit, false, false, STRESS_MODIFIER, false, 10D + RandomUtil.getRandomInt(20) - RandomUtil.getRandomInt(10));

		if (unit instanceof Person) {
			
			// Assume the student is a person.
			Collection<Person> candidates = null;
			List<Person> students = new ArrayList<>();
			
			candidates = getBestStudents(person);
			
			Iterator<Person> i = candidates.iterator();
			while (i.hasNext()) {
				Person candidate = i.next();
				Task task = candidate.getMind().getTaskManager().getTask();
				// Ensure to filter off student performing digging local ice or regolith 
				if (task instanceof DigLocalRegolith || task instanceof DigLocalIce) {
					if (candidate.isInSettlement())
						;// Do NOTHING
					else {
						String loc = person.getLocationTag().getImmediateLocation();
						loc = loc == null ? "[N/A]" : loc;
						loc = loc.equalsIgnoreCase("Outside") ? loc : "in " + loc;
						LogConsolidated.log(logger, Level.INFO, 4000, sourceName, 
								"[" + person.getLocationTag().getLocale() + "] " + person.getName() 
							 + loc + " was " + task.getName());
						students.add(candidate);
					}
				}
				else
					students.add(candidate);
			}
			
			if (students.size() > 0) {
				Object[] array = students.toArray();
				// Randomly get a person student.
				int rand = RandomUtil.getRandomInt(students.size() - 1);
				student = (Person) array[rand];
				teachingTask = student.getMind().getTaskManager().getTask();
				teachingTask.setTeacher(person);
				setDescription(
						Msg.getString("Task.description.teach.detail", teachingTask.getName(false), student.getName())); // $NON-NLS-1$

				boolean walkToBuilding = false;
				// If in settlement, move teacher to building student is in.
				if (person.isInSettlement()) {

					Building studentBuilding = BuildingManager.getBuilding(student);

					if (studentBuilding != null) {
						FunctionType teachingBuildingFunction = teachingTask.getLivingFunction();
						if ((teachingBuildingFunction != null) && (studentBuilding.hasFunction(teachingBuildingFunction))) {
							// Walk to relevant activity spot in student's building.
							walkToActivitySpotInBuilding(studentBuilding, teachingBuildingFunction, false);
						} else {
							// Walk to random location in student's building.
							walkToRandomLocInBuilding(BuildingManager.getBuilding(student), false);
						}
						walkToBuilding = true;
					}
				}

				if (!walkToBuilding) {

					if (person.isInVehicle()) {
						// If person is in rover, walk to passenger activity spot.
						if (person.getVehicle() instanceof Rover) {
							walkToPassengerActivitySpotInRover((Rover) person.getVehicle(), false);
						}
					} else {
						// Walk to random location.
						walkToRandomLocation(true);
					}
				}
			} else {
				endTask();
			}
		}
		
//		else if (unit instanceof Robot) {
//			// Randomly get a student.
//			Collection<Person> candidates = null;
//			List<Person> students = new ArrayList<>();
//			
//			candidates = getBestStudents(robot);
//			
//			if (students.size() > 0) {
//				Object[] array = students.toArray();
//				int rand = RandomUtil.getRandomInt(students.size() - 1);
//				botStudent = (Robot) array[rand];
//				teachingTask = student.getBotMind().getTaskManager().getTask();
//				teachingTask.setTeacher(robot);
//				setDescription(
//						Msg.getString("Task.description.teach.detail", teachingTask.getName(false), student.getName())); // $NON-NLS-1$
//
//				boolean walkToBuilding = false;
//				// If in settlement, move teacher to building student is in.
//				if (robot.isInSettlement()) {
//
//					Building studentBuilding = BuildingManager.getBuilding(student);
//
//					if (studentBuilding != null) {
//						FunctionType teachingBuildingFunction = teachingTask.getLivingFunction();
//						if ((teachingBuildingFunction != null) && (studentBuilding.hasFunction(teachingBuildingFunction))) {
//							// Walk to relevant activity spot in student's building.
//							walkToActivitySpotInBuilding(studentBuilding, teachingBuildingFunction, false);
//						} else {
//							// Walk to random location in student's building.
//							walkToRandomLocInBuilding(BuildingManager.getBuilding(student), false);
//						}
//						walkToBuilding = true;
//					}
//				}
//
//				if (!walkToBuilding) {
//
//					if (robot.isInVehicle()) {
//						// If person is in rover, walk to passenger activity spot.
//						if (robot.getVehicle() instanceof Rover) {
//							walkToPassengerActivitySpotInRover((Rover) robot.getVehicle(), false);
//						}
//					} else {
//						// Walk to random location.
//						walkToRandomLocation(true);
//					}
//				}
//			} else {
//				endTask();
//			}
//		}
		
		// Initialize phase
		addPhase(TEACHING);
		setPhase(TEACHING);
	}

	@Override
	protected double performMappedPhase(double time) {
		if (getPhase() == null) {
			throw new IllegalArgumentException("Task phase is null");
		} else if (TEACHING.equals(getPhase())) {
			return teachingPhase(time);
		} else {
			return time;
		}
	}

	/**
	 * Performs the teaching phase.
	 * 
	 * @param time the amount of time (millisols) to perform the phase.
	 * @return the amount of time (millisols) left over after performing the phase.
	 */
	private double teachingPhase(double time) {

		// Check if task is finished.
		if (teachingTask.isDone()) {
			endTask();
		}
		
    	if (getTimeCompleted() > getDuration())
        	endTask();	

		// Check if student is in a different location situation than the teacher.
//		if (!student.getLocationSituation().equals(person.getLocationSituation())) {
//		if (student.getLocationStateType() != person.getLocationStateType()) {			
//			endTask();
//		}

        // Probability affected by the person's stress and fatigue.
        PhysicalCondition condition = person.getPhysicalCondition();
        double fatigue = condition.getFatigue();
        double stress = condition.getStress();
        double hunger = condition.getHunger();
        double energy = condition.getEnergy();
        
        if (fatigue > 1000 || stress > 75 || hunger > 750 || energy < 500)
        	endTask();   
        
		// Add relationship modifier for opinion of teacher from the student.
		addRelationshipModifier(time);

        // Add experience points
        addExperience(time);
        
		return 0D;
	}

	/**
	 * Adds a relationship modifier for the student's opinion of the teacher.
	 * 
	 * @param time the time teaching.
	 */
	private void addRelationshipModifier(double time) {
		RelationshipManager manager = Simulation.instance().getRelationshipManager();
		double currentOpinion = manager.getOpinionOfPerson(student, person);
		double newOpinion = currentOpinion + (BASE_RELATIONSHIP_MODIFIER * time);
		Relationship relationship = manager.getRelationship(student, person);
		if (relationship != null) {
			relationship.setPersonOpinion(student, newOpinion);
		}
	}

	@Override
	protected void addExperience(double time) {
        // Add experience to associated skill.
        // (1 base experience point per 100 millisols of time spent)
        double exp = time / 100D;

        // Experience points adjusted by person's "Experience Aptitude" attribute.
//        NaturalAttributeManager nManager = person.getNaturalAttributeManager();
//        int teaching = nManager.getAttribute(NaturalAttributeType.TEACHING);
        double mod = getTeachingExperienceModifier() * 150.0;
        exp *= mod;
        
        if (teachingTask != null && teachingTask.getAssociatedSkills() != null) {
        	// Pick one skill to improve upon
        	int rand = RandomUtil.getRandomInt(teachingTask.getAssociatedSkills().size()-1);
        	SkillType taskSkill = teachingTask.getAssociatedSkills().get(rand);
//			Iterator<SkillType> j = teachingTask.getAssociatedSkills().iterator();		
//			while (j.hasNext()) {
//				SkillType taskSkill = j.next();
				int studentSkill = student.getSkillManager().getSkillLevel(taskSkill);
				int teacherSkill = person.getSkillManager().getSkillLevel(taskSkill);
				double studentExp = student.getSkillManager().getCumuativeExperience(taskSkill);
				double teacherExp = person.getSkillManager().getCumuativeExperience(taskSkill);
				double diff = Math.round((teacherExp - studentExp)*10.0)/10.0;
				int points = teacherSkill - studentSkill;
				double learned = (.5 + points) * exp / 1.5 * RandomUtil.getRandomDouble(1);
				double reward = exp / 40.0 * RandomUtil.getRandomDouble(1);
				
//				logger.info(taskSkill.getName() 
//					+ " - diff: " + diff + "   "
//					+ "  mod: " + mod + "   "
//					+ person + " [Lvl : " + teacherSkill + "]'s teaching reward: " + Math.round(reward*1000.0)/1000.0 
//					+ "   " + student + " [Lvl : " + studentSkill + "]'s learned: " + Math.round(learned*1000.0)/1000.0 + ".");
				
				student.getSkillManager().addExperience(taskSkill, learned, time);
		        person.getSkillManager().addExperience(taskSkill, reward, time);
		        
		        // If the student has more experience points than the teacher, the teaching session ends.
		        if (diff < 0)
		        	endTask();
//			}
		}
	}

	@Override
	public void endTask() {
		super.endTask();

		// teachingTask.setTeacher(null);
	}

	/**
	 * Gets a collection of the best students the teacher can teach.
	 * 
	 * @param teacher the teacher looking for students.
	 * @return collection of the best students
	 */
	public static Collection<Person> getBestStudents(Person teacher) {
		Collection<Person> result = new ConcurrentLinkedQueue<Person>();
		Collection<Person> students = getTeachableStudents(teacher);

		// If teacher is in a settlement, best students are in least crowded buildings.
		Collection<Person> leastCrowded = new ConcurrentLinkedQueue<Person>();
		if (teacher.isInSettlement()) {
			// Find the least crowded buildings that teachable students are in.
			int crowding = Integer.MAX_VALUE;
			Iterator<Person> i = students.iterator();
			while (i.hasNext()) {
				Person student = i.next();
				Building building = BuildingManager.getBuilding(student);
				if (building != null) {
					LifeSupport lifeSupport = building.getLifeSupport();
					int buildingCrowding = lifeSupport.getOccupantNumber() - lifeSupport.getOccupantCapacity() + 1;
					if (buildingCrowding < -1) {
						buildingCrowding = -1;
					}
					if (buildingCrowding < crowding) {
						crowding = buildingCrowding;
					}
				}
			}

			// Add students in least crowded buildings to result.
			Iterator<Person> j = students.iterator();
			while (j.hasNext()) {
				Person student = j.next();
				Building building = BuildingManager.getBuilding(student);
				if (building != null) {
					LifeSupport lifeSupport = building.getLifeSupport();
					int buildingCrowding = lifeSupport.getOccupantNumber() - lifeSupport.getOccupantCapacity() + 1;
					if (buildingCrowding < -1) {
						buildingCrowding = -1;
					}
					if (buildingCrowding == crowding) {
						leastCrowded.add(student);
					}
				}
			}
		} else {
			leastCrowded = students;
		}

		// Get the teacher's favorite students.
		RelationshipManager relationshipManager = Simulation.instance().getRelationshipManager();
		Collection<Person> favoriteStudents = new ConcurrentLinkedQueue<Person>();

		// Find favorite opinion.
		double favorite = Double.NEGATIVE_INFINITY;
		Iterator<Person> k = leastCrowded.iterator();
		while (k.hasNext()) {
			Person student = k.next();
			double opinion = relationshipManager.getOpinionOfPerson(teacher, student);
			if (opinion > favorite) {
				favorite = opinion;
			}
		}

		// Get list of favorite students.
		k = leastCrowded.iterator();
		while (k.hasNext()) {
			Person student = k.next();
			double opinion = relationshipManager.getOpinionOfPerson(teacher, student);
			if (opinion == favorite) {
				favoriteStudents.add(student);
			}
		}

		result = favoriteStudents;

		return result;
	}

	/**
	 * Get a collection of students the teacher can teach.
	 * 
	 * @param teacher the teacher looking for students.
	 * @return collection of students
	 */
	private static Collection<Person> getTeachableStudents(Person teacher) {
		Collection<Person> result = new ConcurrentLinkedQueue<Person>();

		Iterator<Person> i = getLocalPeople(teacher).iterator();
		while (i.hasNext()) {
			Person student = i.next();
			boolean possibleStudent = false;
			Task task = student.getMind().getTaskManager().getTask();
			if (task != null && task.getAssociatedSkills() != null) {
				Iterator<SkillType> j = task.getAssociatedSkills().iterator();
				while (j.hasNext()) {
					SkillType taskSkill = j.next();
					int studentSkill = student.getSkillManager().getSkillLevel(taskSkill);
					int teacherSkill = teacher.getSkillManager().getSkillLevel(taskSkill);
					if ((teacherSkill >= (studentSkill + 1)) && !task.hasTeacher()) {
						possibleStudent = true;
					}
				}
				if (possibleStudent) {
					result.add(student);
				}
			}
		}

		return result;
	}

	/**
	 * Get a collection of students the teacher can teach.
	 * 
	 * @param teacher the teacher looking for students.
	 * @return collection of students
	 */
	private static Collection<Person> getTeachableStudents(Robot teacher) {
		Collection<Person> result = new ConcurrentLinkedQueue<Person>();

		Iterator<Person> i = getLocalPeople(teacher).iterator();
		while (i.hasNext()) {
			Person student = i.next();
			boolean possibleStudent = false;
			Task task = student.getMind().getTaskManager().getTask();
			if (task != null) {
				Iterator<SkillType> j = task.getAssociatedSkills().iterator();
				while (j.hasNext()) {
					SkillType taskSkill = j.next();
					int studentSkill = student.getSkillManager().getSkillLevel(taskSkill);
					int teacherSkill = teacher.getSkillManager().getSkillLevel(taskSkill);
					if ((teacherSkill >= (studentSkill + 1)) && !task.hasTeacher()) {
						possibleStudent = true;
					}
				}
				if (possibleStudent) {
					result.add(student);
				}
			}
		}

		return result;
	}
	
	/**
	 * Gets a collection of the best students the teacher can teach.
	 * 
	 * @param teacher the teacher looking for students.
	 * @return collection of the best students
	 */
	public static Collection<Person> getBestStudents(Robot teacher) {
		Collection<Person> result = new ConcurrentLinkedQueue<Person>();
		Collection<Person> students = getTeachableStudents(teacher);

		// If teacher is in a settlement, best students are in least crowded buildings.
		Collection<Person> leastCrowded = new ConcurrentLinkedQueue<Person>();
		if (teacher.isInSettlement()) {
			// Find the least crowded buildings that teachable students are in.
			int crowding = Integer.MAX_VALUE;
			Iterator<Person> i = students.iterator();
			while (i.hasNext()) {
				Person student = i.next();
				Building building = BuildingManager.getBuilding(student);
				if (building != null) {
					LifeSupport lifeSupport = building.getLifeSupport();
					int buildingCrowding = lifeSupport.getOccupantNumber() - lifeSupport.getOccupantCapacity() + 1;
					if (buildingCrowding < -1) {
						buildingCrowding = -1;
					}
					if (buildingCrowding < crowding) {
						crowding = buildingCrowding;
					}
				}
			}

			// Add students in least crowded buildings to result.
			Iterator<Person> j = students.iterator();
			while (j.hasNext()) {
				Person student = j.next();
				Building building = BuildingManager.getBuilding(student);
				if (building != null) {
					LifeSupport lifeSupport = building.getLifeSupport();
					int buildingCrowding = lifeSupport.getOccupantNumber() - lifeSupport.getOccupantCapacity() + 1;
					if (buildingCrowding < -1) {
						buildingCrowding = -1;
					}
					if (buildingCrowding == crowding) {
						leastCrowded.add(student);
					}
				}
			}
		} else {
			leastCrowded = students;
		}

		// TODO : may account for the attitude (like and dislike) of the person having a robot as his tutor 
		
//		// Get the teacher's favorite students.
//		RelationshipManager relationshipManager = Simulation.instance().getRelationshipManager();
//		Collection<Person> favoriteStudents = new ConcurrentLinkedQueue<Person>();
//
//		// Find favorite opinion.
//		double favorite = Double.NEGATIVE_INFINITY;
//		Iterator<Person> k = leastCrowded.iterator();
//		while (k.hasNext()) {
//			Person student = k.next();
//			double opinion = relationshipManager.getOpinionOfPerson(teacher, student);
//			if (opinion > favorite) {
//				favorite = opinion;
//			}
//		}
//
//		// Get list of favorite students.
//		k = leastCrowded.iterator();
//		while (k.hasNext()) {
//			Person student = k.next();
//			double opinion = relationshipManager.getOpinionOfPerson(teacher, student);
//			if (opinion == favorite) {
//				favoriteStudents.add(student);
//			}
//		}

		result = leastCrowded;

		return result;
	}
	
	/**
	 * Gets a collection of people in a person's settlement or rover. The resulting
	 * collection doesn't include the given person.
	 * 
	 * @param person the person checking
	 * @return collection of people
	 */
	private static Collection<Person> getLocalPeople(Person person) {
		Collection<Person> people = new ConcurrentLinkedQueue<Person>();

		if (person.isInSettlement()) {
			Iterator<Person> i = person.getSettlement().getIndoorPeople().iterator();
			while (i.hasNext()) {
				Person inhabitant = i.next();
				if (person != inhabitant) {
					people.add(inhabitant);
				}
			}
		} else if (person.isInVehicle()) {
			Crewable rover = (Crewable) person.getVehicle();
			Iterator<Person> i = rover.getCrew().iterator();
			while (i.hasNext()) {
				Person crewmember = i.next();
				if (person != crewmember) {
					people.add(crewmember);
				}
			}
		}

		return people;
	}

	/**
	 * Gets a collection of robot in a robot's settlement or rover. The resulting
	 * collection doesn't include the given robot.
	 * 
	 * @param robot the robot checking
	 * @return collection of robot
	 */
	private static Collection<Person> getLocalPeople(Robot robot) {
		Collection<Person> people = new ConcurrentLinkedQueue<Person>();

		if (robot.isInSettlement()) {
			Iterator<Person> i = robot.getSettlement().getIndoorPeople().iterator();
			while (i.hasNext()) {
				Person inhabitant = i.next();
//				if (robot != inhabitant) {
					people.add(inhabitant);
//				}
			}
		} else if (robot.isInVehicle()) {
			Crewable rover = (Crewable) robot.getVehicle();
			Iterator<Person> i = rover.getCrew().iterator();
			while (i.hasNext()) {
				Person crewmember = i.next();
//				if (robot != crewmember) {
					people.add(crewmember);
//				}
			}
		}

		return people;
	}
	
	@Override
	public int getEffectiveSkillLevel() {
		return 0;
	}

	@Override
	public List<SkillType> getAssociatedSkills() {
		List<SkillType> results = new ArrayList<SkillType>(0);
		return results;
	}

	@Override
	public void destroy() {
		super.destroy();

		student = null;
		teachingTask = null;
	}
}
