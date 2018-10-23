/**
 * Mars Simulation Project
 * ReviewMissionPlan.java
  * @version 3.1.0 2018-10-10
 * @author Manny Kung
 */
package org.mars_sim.msp.core.person.ai.task;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mars_sim.msp.core.LogConsolidated;
import org.mars_sim.msp.core.Msg;
import org.mars_sim.msp.core.Simulation;
import org.mars_sim.msp.core.person.NaturalAttributeType;
import org.mars_sim.msp.core.person.Person;
import org.mars_sim.msp.core.person.RoleType;
import org.mars_sim.msp.core.person.ai.SkillType;
import org.mars_sim.msp.core.person.ai.job.JobAssignment;
import org.mars_sim.msp.core.person.ai.mission.Mission;
import org.mars_sim.msp.core.person.ai.mission.MissionManager;
import org.mars_sim.msp.core.person.ai.mission.MissionPlanning;
import org.mars_sim.msp.core.person.ai.mission.PlanType;
import org.mars_sim.msp.core.person.ai.social.RelationshipManager;
import org.mars_sim.msp.core.structure.building.Building;
import org.mars_sim.msp.core.structure.building.function.Administration;
import org.mars_sim.msp.core.structure.building.function.FunctionType;
import org.mars_sim.msp.core.tool.RandomUtil;

/**
 * The ReviewMissionPlan class is a task for reviewing job reassignment
 * submission in an office space
 */
public class ReviewMissionPlan extends Task implements Serializable {

	/** default serial id. */
	private static final long serialVersionUID = 1L;

	private static transient Logger logger = Logger.getLogger(ReviewMissionPlan.class.getName());

	private static String sourceName = logger.getName().substring(logger.getName().lastIndexOf(".") + 1,
			logger.getName().length());

	/** Task name */
	private static final String NAME = Msg.getString("Task.description.reviewMissionPlan"); //$NON-NLS-1$

	/** Task phases. */
	private static final TaskPhase REVIEWING = new TaskPhase(
			Msg.getString("Task.phase.reviewMissionPlan")); //$NON-NLS-1$

	private static final TaskPhase FINISHED = new TaskPhase(Msg.getString("Task.phase.reviewMissionPlan.finished")); //$NON-NLS-1$

	// Static members
	/** The stress modified per millisol. */
	private static final double STRESS_MODIFIER = -.1D;

	// Data members
	/** The administration building the person is using. */
	private Administration office;
	/** The role of the person who is reviewing the mission plan. */
	public RoleType roleType;
	
	private static MissionManager missionManager;
	
	private static RelationshipManager relationshipManager;


	/**
	 * Constructor. This is an effort-driven task.
	 * 
	 * @param person the person performing the task.
	 */
	public ReviewMissionPlan(Person person) {
		// Use Task constructor.
		super(NAME, person, true, false, STRESS_MODIFIER, true, 20D);// + RandomUtil.getRandomDouble(100D));

		roleType = person.getRole().getType();
		
		if (person.isInside() && roleType != null) {

			if (roleType == RoleType.PRESIDENT || roleType == RoleType.MAYOR
					|| roleType == RoleType.COMMANDER || roleType == RoleType.SUB_COMMANDER
					|| (roleType == RoleType.MISSION_SPECIALIST && person.getAssociatedSettlement().getNumCitizens() <= 8)) {

				// If person is in a settlement, try to find an office building.
				Building officeBuilding = Administration.getAvailableOffice(person);

				// Note: office building is optional
				if (officeBuilding != null) {
					// Walk to the office building.
//					walkToActivitySpotInBuilding(officeBuilding, false);

					office = officeBuilding.getAdministration();
					office.addstaff();
					
					walkToActivitySpotInBuilding(officeBuilding, true);
				}

				// TODO: add other workplace if administration building is not available

			} // end of roleType
			else {
				endTask();
			}
		}
		else {
			endTask();
		}

		// Initialize phase
		addPhase(REVIEWING);
		addPhase(FINISHED);
		
		setPhase(REVIEWING);
	}

	@Override
	protected FunctionType getLivingFunction() {
		return FunctionType.ADMINISTRATION;
	}

	@Override
	protected double performMappedPhase(double time) {
		if (getPhase() == null) {
			throw new IllegalArgumentException("Task phase is null");
		} else if (REVIEWING.equals(getPhase())) {
			return reviewingPhase(time);
		} else if (FINISHED.equals(getPhase())) {
			return finishedPhase(time);
		} else {
			return time;
		}
	}

	/**
	 * Performs the reviewingPhasephase.
	 * 
	 * @param time the amount of time (millisols) to perform the phase.
	 * @return the amount of time (millisols) left over after performing the phase.
	 */
	private double reviewingPhase(double time) {

//		if (missionManager == null)
        	missionManager = Simulation.instance().getMissionManager();

//		if (relationshipManager == null)
			relationshipManager = Simulation.instance().getRelationshipManager();
		
        List<Mission> missions = missionManager.getPendingMissions(person.getAssociatedSettlement());
 //       System.out.println("# missions : " + missions.size());
		// Iterates through each pending mission 
		Iterator<Mission> i = missions.iterator();
		while (i.hasNext()) {
			Mission m = i.next();
			MissionPlanning mp = m.getPlan();
			
			if (m.getPlan() != null) {
	            PlanType status = mp.getStatus();
	
	            if (status != null && status == PlanType.PENDING) {
	            	
		            if (mp.getPercentComplete() >= 100D) {
		            	// Go to the finished phase and finalize the approval
		            	setPhase(FINISHED);
		            }
		            
		            else {
		            			            
						String reviewedBy = person.getName();
						
						Person p = m.getStartingMember();
						String requester = p.getName();
		
						List<JobAssignment> list = p.getJobHistory().getJobAssignmentList();
						int last = list.size() - 1;
						
						// 1. Reviews requester's cumulative job rating
						double rating = list.get(last).getJobRating();
						double cumulative_rating = 0;
						int size = list.size();
						for (int j = 0; j < size; j++) {
							cumulative_rating += list.get(j).getJobRating();
						}
						cumulative_rating = cumulative_rating / size;
		
						// TODO: Add more depth to this process
						// 2. Weigh in his mission score
						// 3. May go to him/her to have a chat
						// 4. modified by the affinity between them
						// 5. Approve/disapprove the job change
						
						String s = person.getAssociatedSettlement().getName();
						
						int score = (int)(relationshipManager.getOpinionOfPerson(person, p));
						//Math.round(100D * relationshipManager.getOpinionOfPerson(person, p))/100D;
						
						score = (int)((rating + cumulative_rating) / 5D * score + RandomUtil.getRandomInt(10) - RandomUtil.getRandomInt(5));
								
						// Updates the mission plan status
						missionManager.scoreMissionPlan(mp, score);
						
						LogConsolidated.log(logger, Level.INFO, 5000, sourceName, 
								"[" + s + "] " + reviewedBy + " has given " + requester
								+ "'s " + m.getDescription() + " mission plan a score of " + score + ".", null);
						
					      // Add experience
				        addExperience(time);
			        
						// Do only one review each time
						break;
		            }
				}
			}
		} // end of while
		
        return 0;
	}

	/**
	 * Performs the finished phase.
	 * 
	 * @param time the amount of time (millisols) to perform the phase.
	 * @return the amount of time (millisols) left over after performing the phase.
	 */
	private double finishedPhase(double time) {

//		if (missionManager == null)
        missionManager = Simulation.instance().getMissionManager();
        
        List<Mission> missions = missionManager.getPendingMissions(person.getAssociatedSettlement());
 		// Iterates through each pending mission 
		Iterator<Mission> i = missions.iterator();
		while (i.hasNext()) {
			Mission m = i.next();		
			MissionPlanning mp = m.getPlan();
			
			if (mp != null) {
	            PlanType status = mp.getStatus();
	
	            if (status != null && status == PlanType.PENDING) {
	            	
					String reviewedBy = person.getName();
					
					Person p = m.getStartingMember();
					String requester = p.getName();
				
					String s = person.getAssociatedSettlement().getName();
					
					double score = mp.getScore();
					
					if (score < 500) {
						// not approved
						// Updates the mission plan status
						missionManager.approveMissionPlan(mp, p, PlanType.NOT_APPROVED);
					
						LogConsolidated.log(logger, Level.INFO, 0, sourceName, 
								"[" + s + "] " + reviewedBy + " did NOT approve " + requester
								+ "'s " + m.getDescription() + " mission plan. Try again when the performance rating is higher.", null);
					} else {
						// Updates the mission plan status
						missionManager.approveMissionPlan(mp, p, PlanType.APPROVED);
							
						LogConsolidated.log(logger, Level.INFO, 0, sourceName,
								"[" + s + "] " + reviewedBy + " just approved " + requester
								+ "'s " + m.getDescription() + " mission plan.", null);
					}
										
				      // Add experience
			        addExperience(time);
		        
					// Do only one review each time
					break;
				}
			}
		} // end of while
		
        return 0;
	}

	
	@Override
	protected void addExperience(double time) {
        double newPoints = time / 20D;
        int experienceAptitude = person.getNaturalAttributeManager().getAttribute(
                NaturalAttributeType.EXPERIENCE_APTITUDE);
        int leadershipAptitude = person.getNaturalAttributeManager().getAttribute(
                NaturalAttributeType.LEADERSHIP);
        newPoints += newPoints * (experienceAptitude + leadershipAptitude- 100D) / 100D;
        newPoints *= getTeachingExperienceModifier();
        person.getMind().getSkillManager().addExperience(SkillType.MANAGEMENT, newPoints);

	}

	@Override
	public void endTask() {
		super.endTask();

		// Remove person from administration function so others can use it.
		if (office != null && office.getNumStaff() > 0) {
			office.removeStaff();
		}
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

		office = null;
	}
}