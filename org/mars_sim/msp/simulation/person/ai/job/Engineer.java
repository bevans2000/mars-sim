/**
 * Mars Simulation Project
 * Engineer.java
 * @version 2.77 2004-08-23
 * @author Scott Davis
 */
package org.mars_sim.msp.simulation.person.ai.job;

import java.io.Serializable;
import org.mars_sim.msp.simulation.person.*;
import org.mars_sim.msp.simulation.person.ai.mission.*;
import org.mars_sim.msp.simulation.person.ai.task.*;
import org.mars_sim.msp.simulation.structure.Settlement;

/** 
 * The Engineer class represents an engineer job focusing on repair and maintenance of buildings and vehicles.
 */
public class Engineer extends Job implements Serializable {

	/**
	 * Constructor
	 */
	public Engineer() {
		// Use Job constructor
		super("Engineer");
		
		// Add engineer-related tasks.
		jobTasks.add(Maintenance.class);
		jobTasks.add(MaintenanceEVA.class);
		jobTasks.add(MaintainGroundVehicleGarage.class);
		jobTasks.add(MaintainGroundVehicleEVA.class);
		jobTasks.add(RepairMalfunction.class);
		jobTasks.add(RepairEVAMalfunction.class);
		jobTasks.add(EnterAirlock.class);
		jobTasks.add(ExitAirlock.class);
		
		// Add engineer-related missions.
		jobMissionStarts.add(TravelToSettlement.class);
		jobMissionJoins.add(TravelToSettlement.class);	
	}

	/**
	 * Gets a person's capability to perform this job.
	 * @param person the person to check.
	 * @return capability (min 0.0).
	 */
	public double getCapability(Person person) {
		
		double result = 0D;
		
		int areologySkill = person.getSkillManager().getSkillLevel(Skill.MECHANICS);
		result = areologySkill;
		
		NaturalAttributeManager attributes = person.getNaturalAttributeManager();
		int experienceAptitude = attributes.getAttribute("Experience Aptitude");
		result+= result * ((experienceAptitude - 50D) / 100D);
		
		return result;
	}
	
	/**
	 * Gets the base settlement need for this job.
	 * @param settlement the settlement in need.
	 * @return the base need >= 0
	 */
	public double getSettlementNeed(Settlement settlement) {
		
		double result = 0D;
		
		// Add number of buildings in settlement.
		result+= settlement.getBuildingManager().getBuildingNum();
		
		// Add number of vehicles parked at settlement.
		result+= settlement.getParkedVehicleNum();
		
		return result;	
	}
}