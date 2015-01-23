/**
 * Mars Simulation Project
 * JobManager.java
 * @version 3.07 2015-01-21

 * @author Scott Davis
 */
package org.mars_sim.msp.core.person.ai.job;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mars_sim.msp.core.Unit;
import org.mars_sim.msp.core.person.LocationSituation;
import org.mars_sim.msp.core.person.Person;
import org.mars_sim.msp.core.person.Robot;
import org.mars_sim.msp.core.structure.Settlement;

/** 
 * The JobManager class keeps track of the settler jobs in a simulation.
 */
public final class JobManager implements Serializable {

	/** default serial id. */
	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(JobManager.class.getName());

	// Data members
	/** List of the jobs in the simulation. */
	private static List<Job> jobs; 

	/**
	 * Private constructor for static utility class.
	 */
	private JobManager() {}

	/**
	 * Initialize job list.
	 */
	private static void loadJobs() {
		jobs = new ArrayList<Job>();
		jobs.add(new Botanist());
		jobs.add(new Areologist());
		jobs.add(new Doctor());
		jobs.add(new Engineer());
		jobs.add(new Driver());
		jobs.add(new Chef());
		jobs.add(new Trader());
		jobs.add(new Technician());
		jobs.add(new Architect());
		jobs.add(new Biologist());
		jobs.add(new Astronomer());
		jobs.add(new Chemist());
		jobs.add(new Physicist());
		jobs.add(new Mathematician());
		jobs.add(new Meteorologist());
	}

	/**
	 * Gets a list of available jobs in the simulation.
	 * @return list of jobs
	 */
	public static List<Job> getJobs() {
		if (jobs == null) loadJobs();
		return new ArrayList<Job>(jobs);
	}

	/**
	 * Gets a job from a job class name.
	 * @param jobName the name of the job.
	 * @return job or null if job with name not found.
	 */
	public static Job getJob(String jobClassName) {
		if (jobs == null) loadJobs();
		for (Job job : jobs) {
			if (job.getClass().getSimpleName().compareTo(jobClassName) == 0) {
				return job;
			}
		}
		return null;
	}

	/**
	 * Gets the need for a job at a settlement minus the capability of the inhabitants
	 * performing that job there.
	 * @param settlement the settlement to check.
	 * @param job the job to check.
	 * @return settlement need minus total job capability of inhabitants with job.
	 */
	public static double getRemainingSettlementNeed(Settlement settlement, Job job) {
		double result = job.getSettlementNeed(settlement);


			// Check all people associated with the settlement.
			Iterator<Person> i = settlement.getAllAssociatedPeople().iterator();
			while (i.hasNext()) {
				Person person = i.next();
				if (person.getMind().getJob() == job) result-= job.getCapability(person);
			}
	

			// Check all Robots associated with the settlement.
			Iterator<Robot> j = settlement.getAllAssociatedRobots().iterator();
			while (j.hasNext()) {
				Robot robot = j.next();
				if (robot.getMind().getJob() == job) result-= job.getCapability(robot);
			}
	
		return result;
	}

	/**
	 * Gets a new job for the person.
	 * Might be the person's current job.
	 * @param person the person to check.
	 * @return the new job.
	 */
	public static Job getNewJob(Unit unit) {
		Job newJob = null;
        Person person = null;
        Robot robot = null;
        
        if (unit instanceof Person) {
         	person = (Person) unit;
    		Job originalJob = person.getMind().getJob();
    		// Determine person's associated settlement.
    		Settlement settlement = null;
    		if (person.getLocationSituation() == LocationSituation.IN_SETTLEMENT) 
    			settlement = person.getSettlement();
    		else if (person.getMind().hasActiveMission()) 
    			settlement = person.getMind().getMission().getAssociatedSettlement();

    		// Find new job for person.
    		double newJobProspect = Integer.MIN_VALUE;					
    		if (settlement != null) {
    			Iterator<Job> i = getJobs().iterator();
    			while (i.hasNext()) {
    				Job job = i.next();
    				double jobProspect = getJobProspect(person, job, settlement, true);
    				if (jobProspect >= newJobProspect) {
    					newJob = job;
    					newJobProspect = jobProspect;
    				}
    			}

    			if(logger.isLoggable(Level.FINEST)) {
    				if ((newJob != null) && (newJob != originalJob)) 
    					logger.finest(person.getName() + " changed jobs to " + newJob.getName(person.getGender()));
    				else logger.finest(person.getName() + " keeping old job of " + originalJob.getName(person.getGender()));

    			}
    		}
    		else newJob = originalJob;

        }
        else if (unit instanceof Robot) {
        	robot = (Robot) unit;
			Job originalJob = robot.getMind().getJob();
	
			// Determine robot's associated settlement.
			Settlement settlement = null;
			if (robot.getLocationSituation() == LocationSituation.IN_SETTLEMENT) 
				settlement = robot.getSettlement();
			else if (robot.getMind().hasActiveMission()) 
				settlement = robot.getMind().getMission().getAssociatedSettlement();
	
			// Find new job for robot.
			double newJobProspect = Integer.MIN_VALUE;					
			if (settlement != null) {
				Iterator<Job> i = getJobs().iterator();
				while (i.hasNext()) {
					Job job = i.next();
					double jobProspect = getJobProspect(robot, job, settlement, true);
					if (jobProspect >= newJobProspect) {
						newJob = job;
						newJobProspect = jobProspect;
					}
				}
	
				if(logger.isLoggable(Level.FINEST)) {
					if ((newJob != null) && (newJob != originalJob)) 
						logger.finest(robot.getName() + " changed jobs to " + newJob.getName(robot.getRobotType()));
					else logger.finest(robot.getName() + " keeping old job of " + originalJob.getName(robot.getRobotType()));
				}	
			}
			else newJob = originalJob;
        }
		return newJob;
	}

	/**
	 * Get the job prospect value for a person and a particular job at a settlement.
	 * @param person the person to check for
	 * @param job the job to check for
	 * @param settlement the settlement to do the job in.
	 * @param isHomeSettlement is this the person's home settlement?
	 * @return job prospect value (0.0 min)
	 */
	public static double getJobProspect(Unit unit, Job job, Settlement settlement, boolean isHomeSettlement) {
        Person person = null;
        Robot robot = null;
		double jobCapability = 0D;
		double remainingNeed = 0D;
		
        if (unit instanceof Person) {
         	person = (Person) unit;
        	if (job != null) jobCapability = job.getCapability(person);   		
    		remainingNeed = getRemainingSettlementNeed(settlement, job);   		
    		if ((job == person.getMind().getJob()) && isHomeSettlement) remainingNeed+= jobCapability;
     
        }
        else if (unit instanceof Robot) {
        	robot = (Robot) unit;
        	if (job != null) jobCapability = job.getCapability(robot);    		
    		remainingNeed = getRemainingSettlementNeed(settlement, job);    		
    		if ((job == robot.getMind().getJob()) && isHomeSettlement) remainingNeed+= jobCapability;
        }
		return (jobCapability + 1D) * remainingNeed;
	}

	/**
	 * Gets the best job prospect value for a person at a settlement.
	 * @param person the person to check for
	 * @param settlement the settlement to do the job in
	 * @param isHomeSettlement is this the person's home settlement?
	 * @return best job prospect value
	 */
	public static double getBestJobProspect(Person person, Settlement settlement, boolean isHomeSettlement) {
		double bestProspect = Double.MIN_VALUE;
		Iterator<Job> i = getJobs().iterator();
		while (i.hasNext()) {
			Job job = i.next();
			double prospect = getJobProspect(person, job, settlement, isHomeSettlement);
			if (prospect > bestProspect) bestProspect = prospect;
		}
		return bestProspect;
	}
}