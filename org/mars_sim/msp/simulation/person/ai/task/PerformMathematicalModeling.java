/**
 * Mars Simulation Project
 * PerformMathematicalModeling.java
 * @version 2.87 2009-08-06
 * @author Scott Davis
 */
package org.mars_sim.msp.simulation.person.ai.task;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mars_sim.msp.simulation.Lab;
import org.mars_sim.msp.simulation.RandomUtil;
import org.mars_sim.msp.simulation.Simulation;
import org.mars_sim.msp.simulation.malfunction.MalfunctionManager;
import org.mars_sim.msp.simulation.person.NaturalAttributeManager;
import org.mars_sim.msp.simulation.person.Person;
import org.mars_sim.msp.simulation.person.ai.SkillManager;
import org.mars_sim.msp.simulation.person.ai.job.Job;
import org.mars_sim.msp.simulation.science.Science;
import org.mars_sim.msp.simulation.science.ScienceUtil;
import org.mars_sim.msp.simulation.science.ScientificStudy;
import org.mars_sim.msp.simulation.science.ScientificStudyManager;
import org.mars_sim.msp.simulation.structure.building.Building;
import org.mars_sim.msp.simulation.structure.building.BuildingException;
import org.mars_sim.msp.simulation.structure.building.BuildingManager;
import org.mars_sim.msp.simulation.structure.building.function.Research;
import org.mars_sim.msp.simulation.vehicle.Rover;
import org.mars_sim.msp.simulation.vehicle.Vehicle;

/**
 * A task for performing mathematical modeling in a laboratory for a scientific study.
 */
public class PerformMathematicalModeling extends Task implements Serializable {

    private static String CLASS_NAME = "org.mars_sim.msp.simulation.person.ai." + 
            "task.PerformMathematicalModeling";
    private static Logger logger = Logger.getLogger(CLASS_NAME);
    
    // The stress modified per millisol.
    private static final double STRESS_MODIFIER = -.2D; 

    // Task phase.
    private static final String MODELING = "Modeling";

    // Data members.
    private ScientificStudy study; // The scientific study the person is modeling for.
    private Lab lab;               // The laboratory the person is working in.
    private MalfunctionManager malfunctions; // The lab's associated malfunction manager.
    
    /**
     * Constructor
     * @param person the person performing the task.
     * @throws Exception if error creating task.
     */
    public PerformMathematicalModeling(Person person) throws Exception {
        // Use task constructor.
        super("Perform mathematical modeling", person, true, false, STRESS_MODIFIER, 
                true, RandomUtil.getRandomDouble(200D));
        
        // Determine study.
        study = determineStudy();
        if (study != null) {
            lab = getLocalLab(person);
            if (lab != null) {
                addPersonToLab();
            }
            else {
                logger.info("lab could not be determined.");
                endTask();
            }
        }
        else {
            logger.info("study could not be determined");
            endTask();
        }
        
        // Initialize phase
        addPhase(MODELING);
        setPhase(MODELING);
    }
    
    /** 
     * Returns the weighted probability that a person might perform this task.
     * @param person the person to perform the task
     * @return the weighted probability that a person might perform this task
     */
    public static double getProbability(Person person) {
        double result = 0D;
        
        Science mathematicsScience = ScienceUtil.getScience(Science.MATHEMATICS);
        
        // Add probability for researcher's primary study (if any).
        ScientificStudyManager studyManager = Simulation.instance().getScientificStudyManager();
        ScientificStudy primaryStudy = studyManager.getOngoingPrimaryStudy(person);
        if ((primaryStudy != null) && ScientificStudy.RESEARCH_PHASE.equals(primaryStudy.getPhase())) {
            if (!primaryStudy.isPrimaryResearchCompleted()) {
                if (mathematicsScience.equals(primaryStudy.getScience())) {
                    try {
                        Lab lab = getLocalLab(person);
                        if (lab != null) {
                            double primaryResult = 100D;
                    
                            // Get lab building crowding modifier.
                            primaryResult *= getLabCrowdingModifier(person, lab);
                    
                            // If researcher's current job isn't related to study science, divide by two.
                            Job job = person.getMind().getJob();
                            if (job != null) {
                                Science jobScience = ScienceUtil.getAssociatedScience(job);
                                if (!primaryStudy.getScience().equals(jobScience)) primaryResult /= 2D;
                            }
                    
                            result += primaryResult;
                        }
                    }
                    catch (Exception e) {
                        logger.severe("getProbability(): " + e.getMessage());
                    }
                }
            }
        }
        
        // Add probability for each study researcher is collaborating on.
        Iterator<ScientificStudy> i = studyManager.getOngoingCollaborativeStudies(person).iterator();
        while (i.hasNext()) {
            ScientificStudy collabStudy = i.next();
            if (ScientificStudy.RESEARCH_PHASE.equals(collabStudy.getPhase())) {
                if (!collabStudy.isCollaborativeResearchCompleted(person)) {
                    Science collabScience = collabStudy.getCollaborativeResearchers().get(person);
                    if (mathematicsScience.equals(collabScience)) {
                        try {
                            Lab lab = getLocalLab(person);
                            if (lab != null) {
                                double collabResult = 50D;
                        
                                // Get lab building crowding modifier.
                                collabResult *= getLabCrowdingModifier(person, lab);
                        
                                // If researcher's current job isn't related to study science, divide by two.
                                Job job = person.getMind().getJob();
                                if (job != null) {
                                    Science jobScience = ScienceUtil.getAssociatedScience(job);
                                    if (!collabScience.equals(jobScience)) collabResult /= 2D;
                                }
                        
                                result += collabResult;
                            }
                        }
                        catch (Exception e) {
                            logger.severe("getProbability(): " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        // Effort-driven task modifier.
        result *= person.getPerformanceRating();
        
        return result;
    }
    
    /**
     * Gets the crowding modifier for a researcher to use a given laboratory building.
     * @param researcher the researcher.
     * @param lab the laboratory.
     * @return crowding modifier.
     * @throws BuildingException if error determining lab building.
     */
    private static double getLabCrowdingModifier(Person researcher, Lab lab) 
            throws BuildingException {
        double result = 1D;
        if (researcher.getLocationSituation().equals(Person.INSETTLEMENT)) {
            Building labBuilding = ((Research) lab).getBuilding();  
            if (labBuilding != null) {
                result *= Task.getCrowdingProbabilityModifier(researcher, labBuilding);     
                result *= Task.getRelationshipModifier(researcher, labBuilding);
            }
        }
        return result;
    }
    
    /**
     * Determines the scientific study that will be researched.
     * @return study or null if none available.
     */
    private ScientificStudy determineStudy() {
        ScientificStudy result = null;
        
        Science mathematicsScience = ScienceUtil.getScience(Science.MATHEMATICS);
        
        List<ScientificStudy> possibleStudies = new ArrayList<ScientificStudy>();
        
        // Add primary study if mathmatics and in research phase.
        ScientificStudyManager manager = Simulation.instance().getScientificStudyManager();
        ScientificStudy primaryStudy = manager.getOngoingPrimaryStudy(person);
        if (primaryStudy != null) {
            if (ScientificStudy.RESEARCH_PHASE.equals(primaryStudy.getPhase()) && 
                    !primaryStudy.isPrimaryResearchCompleted()) {
                if (mathematicsScience.equals(primaryStudy.getScience())) {
                    // Primary study added twice to double chance of random selection.
                    possibleStudies.add(primaryStudy);
                    possibleStudies.add(primaryStudy);
                }
            }
        }
        
        // Add all collaborative studies with mathematics and in research phase.
        Iterator<ScientificStudy> i = manager.getOngoingCollaborativeStudies(person).iterator();
        while (i.hasNext()) {
            ScientificStudy collabStudy = i.next();
            if (ScientificStudy.RESEARCH_PHASE.equals(collabStudy.getPhase()) && 
                    !collabStudy.isCollaborativeResearchCompleted(person)) {
                Science collabScience = collabStudy.getCollaborativeResearchers().get(person);
                if (mathematicsScience.equals(collabScience)) {
                    possibleStudies.add(collabStudy);
                }
            }
        }
        
        // Randomly select study.
        if (possibleStudies.size() > 0) {
            int selected = RandomUtil.getRandomInt(possibleStudies.size() - 1);
            result = possibleStudies.get(selected);
        }
        
        return result;
    }
    
    /**
     * Gets a local lab for mathematical modeling.
     * @param person the person checking for the lab.
     * @return laboratory found or null if none.
     * @throws Exception if error getting a lab.
     */
    private static Lab getLocalLab(Person person) throws Exception {
        Lab result = null;
        
        String location = person.getLocationSituation();
        if (location.equals(Person.INSETTLEMENT)) result = getSettlementLab(person);
        else if (location.equals(Person.INVEHICLE)) result = getVehicleLab(person.getVehicle());
        
        return result;
    }
    
    /**
     * Gets a settlement lab for mathematical modeling.
     * @param person the person looking for a lab.
     * @return a valid modeling lab.
     */
    private static Lab getSettlementLab(Person person) {
        Lab result = null;
        
        try {
            BuildingManager manager = person.getSettlement().getBuildingManager();
            List<Building> labBuildings = manager.getBuildings(Research.NAME);
            labBuildings = getSettlementLabsWithMathematicsSpeciality(labBuildings);
            labBuildings = BuildingManager.getNonMalfunctioningBuildings(labBuildings);
            labBuildings = getSettlementLabsWithAvailableSpace(labBuildings);
            labBuildings = BuildingManager.getLeastCrowdedBuildings(labBuildings);
            labBuildings = BuildingManager.getBestRelationshipBuildings(person, labBuildings);
        
            if (labBuildings.size() > 0) {
                Building building = (Building) labBuildings.get(0);
                result = (Research) building.getFunction(Research.NAME);
            }
        }
        catch (BuildingException e) {
            logger.severe("getSettlementLab(): " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Gets a list of research buildings with available research space from a list of buildings 
     * with the research function.
     * @param buildingList list of buildings with research function.
     * @return research buildings with available lab space.
     * @throws BuildingException if building list contains buildings without research function.
     */
    private static List<Building> getSettlementLabsWithAvailableSpace(List<Building> buildingList) 
            throws BuildingException {
        List<Building> result = new ArrayList<Building>();
        
        Iterator<Building> i = buildingList.iterator();
        while (i.hasNext()) {
            Building building = i.next();
            Research lab = (Research) building.getFunction(Research.NAME);
            if (lab.getResearcherNum() < lab.getLaboratorySize()) result.add(building);
        }
        
        return result;
    }
    
    /**
     * Gets a list of research buildings with mathemtics speciality from a list of 
     * buildings with the research function.
     * @param buildingList list of buildings with research function.
     * @return research buildings with mathematics speciality.
     * @throws BuildingException if building list contains buildings without research function.
     */
    private static List<Building> getSettlementLabsWithMathematicsSpeciality(List buildingList) 
            throws BuildingException {
        List<Building> result = new ArrayList<Building>();
        
        Science mathematicsScience = ScienceUtil.getScience(Science.MATHEMATICS);
        
        Iterator i = buildingList.iterator();
        while (i.hasNext()) {
            Building building = (Building) i.next();
            Research lab = (Research) building.getFunction(Research.NAME);
            if (lab.hasSpeciality(mathematicsScience.getName())) result.add(building);
        }
        
        return result;
    }
    
    /**
     * Gets an available lab in a vehicle.
     * Returns null if no lab is currently available.
     * @param vehicle the vehicle
     * @return available lab
     */
    private static Lab getVehicleLab(Vehicle vehicle) {
        
        Lab result = null;
        
        Science mathematicsScience = ScienceUtil.getScience(Science.MATHEMATICS);
        
        if (vehicle instanceof Rover) {
            Rover rover = (Rover) vehicle;
            if (rover.hasLab()) {
                Lab lab = rover.getLab();
                boolean availableSpace = (lab.getResearcherNum() < lab.getLaboratorySize());
                boolean speciality = lab.hasSpeciality(mathematicsScience.getName());
                boolean malfunction = (rover.getMalfunctionManager().hasMalfunction());
                if (availableSpace && speciality && !malfunction) result = lab;
            }
        }
        
        return result;
    }
    
    /**
     * Adds a person to a lab.
     */
    private void addPersonToLab() {
        
        try {
            String location = person.getLocationSituation();
            if (location.equals(Person.INSETTLEMENT)) {
                Building labBuilding = ((Research) lab).getBuilding();
                BuildingManager.addPersonToBuilding(person, labBuilding);
                lab.addResearcher();
                malfunctions = labBuilding.getMalfunctionManager();
            }
            else if (location.equals(Person.INVEHICLE)) {
                lab.addResearcher();
                malfunctions = person.getVehicle().getMalfunctionManager();
            }
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "addPersonToLab(): " + e.getMessage());
        }
    }
    
    @Override
    protected void addExperience(double time) {
        // Add experience to mathematics skill
        // (1 base experience point per 20 millisols of modeling time)
        // Experience points adjusted by person's "Academic Aptitude" attribute.
        double newPoints = time / 20D;
        int academicAptitude = person.getNaturalAttributeManager().getAttribute(
            NaturalAttributeManager.ACADEMIC_APTITUDE);
        newPoints += newPoints * ((double) academicAptitude - 50D) / 100D;
        newPoints *= getTeachingExperienceModifier();
        Science mathematicsScience = ScienceUtil.getScience(Science.MATHEMATICS);
        String mathematicsSkill = ScienceUtil.getAssociatedSkill(mathematicsScience);
        person.getMind().getSkillManager().addExperience(mathematicsSkill, newPoints);
    }

    @Override
    public List<String> getAssociatedSkills() {
        List<String> results = new ArrayList<String>(1);
        Science mathematicsScience = ScienceUtil.getScience(Science.MATHEMATICS);
        String mathematicsSkill = ScienceUtil.getAssociatedSkill(mathematicsScience);
        results.add(mathematicsSkill);
        return results;
    }

    @Override
    public int getEffectiveSkillLevel() {
        Science mathematicsScience = ScienceUtil.getScience(Science.MATHEMATICS);
        String mathematicsSkill = ScienceUtil.getAssociatedSkill(mathematicsScience);
        SkillManager manager = person.getMind().getSkillManager();
        return manager.getEffectiveSkillLevel(mathematicsSkill);
    }
    
    /**
     * Gets the effective mathematical modeling time based on the person's mathematics skill.
     * @param time the real amount of time (millisol) for modeling.
     * @return the effective amount of time (millisol) for modeling.
     */
    private double getEffectiveModelingTime(double time) {
        // Determine effective research time based on the mathematics skill.
        double modelingTime = time;
        int mathematicsSkill = getEffectiveSkillLevel();
        if (mathematicsSkill == 0) modelingTime /= 2D;
        if (mathematicsSkill > 1) modelingTime += modelingTime * (.2D * mathematicsSkill);
        
        // Modify by tech level of laboratory.
        int techLevel = lab.getTechnologyLevel();
        if (techLevel > 0) modelingTime *= techLevel;
        
        return modelingTime;
    }

    @Override
    protected double performMappedPhase(double time) throws Exception {
        if (getPhase() == null) throw new IllegalArgumentException("Task phase is null");
        if (MODELING.equals(getPhase())) return modelingPhase(time);
        else return time;
    }
    
    /**
     * Performs the mathematical modeling phase.
     * @param time the amount of time (millisols) to perform the phase.
     * @return the amount of time (millisols) left over after performing the phase.
     * @throws Exception if error performing the phase.
     */
    private double modelingPhase(double time) throws Exception {
        // If person is incapacitated, end task.
        if (person.getPerformanceRating() == 0D) endTask();
        
        // Check for laboratory malfunction.
        if (malfunctions.hasMalfunction()) endTask();
        
        // Check if research in study is completed.
        boolean isPrimary = study.getPrimaryResearcher().equals(person);
        if (isPrimary) {
            if (study.isPrimaryResearchCompleted()) endTask();
        }
        else {
            if (study.isCollaborativeResearchCompleted(person)) endTask();
        }
        
        if (isDone()) return time;
        
        // Add modeling work time to study.
        double modelingTime = getEffectiveModelingTime(time);
        if (isPrimary) study.addPrimaryResearchWorkTime(modelingTime);
        else study.addCollaborativeResearchWorkTime(person, modelingTime);
        
        // Add experience
        addExperience(modelingTime);
        
        // Check for lab accident.
        checkForAccident(time);
        
        return 0D;
    }
    
    /**
     * Check for accident in laboratory.
     * @param time the amount of time researching (in millisols)
     */
    private void checkForAccident(double time) {

        double chance = .001D;

        // Mathematics skill modification.
        int skill = getEffectiveSkillLevel();
        if (skill <= 3) chance *= (4 - skill);
        else chance /= (skill - 2);

        if (RandomUtil.lessThanRandPercent(chance * time)) {
            logger.info(person.getName() + " has a lab accident while performing " + 
                    "mathematical modeling");
            if (person.getLocationSituation().equals(Person.INSETTLEMENT)) 
                ((Research) lab).getBuilding().getMalfunctionManager().accident();
            else if (person.getLocationSituation().equals(Person.INVEHICLE)) 
                person.getVehicle().getMalfunctionManager().accident(); 
        }
    }
}