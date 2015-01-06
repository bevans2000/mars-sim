/**
 * Mars Simulation Project
 * PhysicalCondition.java
 * @version 3.07 2015-01-04
 * @author Barry Evans
 */
package org.mars_sim.msp.core.person;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mars_sim.msp.core.LifeSupport;
import org.mars_sim.msp.core.RandomUtil;
import org.mars_sim.msp.core.Simulation;
import org.mars_sim.msp.core.SimulationConfig;
import org.mars_sim.msp.core.Unit;
import org.mars_sim.msp.core.UnitEventType;
import org.mars_sim.msp.core.person.medical.Complaint;
import org.mars_sim.msp.core.person.medical.DeathInfo;
import org.mars_sim.msp.core.person.medical.HealthProblem;
import org.mars_sim.msp.core.person.medical.MedicalEvent;
import org.mars_sim.msp.core.person.medical.MedicalManager;
import org.mars_sim.msp.core.person.medical.Medication;
import org.mars_sim.msp.core.resource.AmountResource;

/**
 * This class represents the Physical Condition of a Person. It models the
 * Persons health and physical characteristics.
 */
public class PhysicalCondition
implements Serializable {

    /** default serial id. */
    private static final long serialVersionUID = 1L;

    /** default logger. */
    private static Logger logger = Logger.getLogger(PhysicalCondition.class.getName());

    /** Life support minimum value. */
    private static int MIN_VALUE = 0;
    /** Life support maximum value. */
    private static int MAX_VALUE = 1;

    /** Stress jump resulting from being in an accident. */
    public static final double ACCIDENT_STRESS = 40D;

    /** TODO The anxiety attack health complaint should be an enum or smth. */
    private static final String ANXIETY_ATTACK = "Anxiety Attack";
    
    /** Period of time (millisols) over which random ailments may happen. */
    private static double RANDOM_AILMENT_PROBABILITY_TIME = 100000D;

    // Data members
    /** Details of persons death. */
    private DeathInfo deathDetails;
    /** Injury/Illness effecting person. */
    private HashMap<Complaint, HealthProblem> problems;
    /** Most serious problem. */
    private HealthProblem serious;
    /** Person's fatigue level. */
    private double fatigue;
    /** Person's hunger level. */
    private double hunger;
    /** Person's stress level (0.0 - 100.0). */
    private double stress;
    /** Performance factor. */
    private double performance;
    /** Person owning this physical. */
    private Person person;
    /** True if person is alive. */
    private boolean alive;
    /** List of medication affecting the person. */
    private List<Medication> medicationList;

    /**
     * Constructor.
     * @param newPerson The person requiring a physical presence.
     */
    public PhysicalCondition(Person newPerson) {
        deathDetails = null;
        person = newPerson;
        problems = new HashMap<Complaint, HealthProblem>();
        performance = 1.0D;
        fatigue = RandomUtil.getRandomDouble(1000D);
        hunger = RandomUtil.getRandomDouble(1000D);
        stress = RandomUtil.getRandomDouble(100D);
        alive = true;
        medicationList = new ArrayList<Medication>();
    }

    /**
     * Gets the medical manager.
     * @return medical manager.
     */
    private MedicalManager getMedicalManager() {
        return Simulation.instance().getMedicalManager();
    }

    /**
     * The Physical condition should be updated to reflect a passing of time.
     * This method has to check the recover or degradation of any current
     * illness. The progression of this time period may result in the illness
     * turning fatal.
     * It also updated the hunger and fatigue status
     *
     * @param time amount of time passing (in millisols)
     * @param support life support system.
     * @param config person configuration.
     * @return True still alive.
     */
    boolean timePassing(double time, LifeSupport support,
            PersonConfig config) {

        boolean illnessEvent = false;

        // Check the existing problems
        if (!problems.isEmpty()) {
            // Throw illness event if any problems already exist.
            illnessEvent = true;

            List<Complaint> newProblems = new ArrayList<Complaint>();
            List<HealthProblem> currentProblems = new ArrayList<HealthProblem>(problems.values());

            Iterator<HealthProblem> iter = currentProblems.iterator();
            while(!isDead() && iter.hasNext()) {
                HealthProblem problem = iter.next();

                // Advance each problem, they may change into a worse problem.
                // If the current is completed or a new problem exists then
                // remove this one.
                Complaint next = problem.timePassing(time, this);

                if (problem.getCured() || (next != null)) {
                    problems.remove(problem.getIllness());
                }

                // If a new problem, check it doesn't exist already
                if (next != null) {
                    newProblems.add(next);
                }
            }

            // Add the new problems
            Iterator<Complaint> newIter = newProblems.iterator();
            while(newIter.hasNext()) {
                addMedicalComplaint(newIter.next());
                illnessEvent = true;
            }
        }

        // Has the person died ?
        if (isDead()) return false;

        // See if any random illnesses happen.
        List<Complaint> randomAilments = checkForRandomAilments(time);
        if (randomAilments.size() > 0) {
            illnessEvent = true;
        }

        // Consume necessary oxygen and water.
        try {
            if (consumeOxygen(support, getOxygenConsumptionRate() * (time / 1000D)))
                logger.log(Level.SEVERE, person.getName() + " has insufficient oxygen.");
            if (consumeWater(support, getWaterConsumptionRate() * (time / 1000D)))
                logger.log(Level.SEVERE, person.getName() + " has insufficient water.");
            if (requireAirPressure(support, config.getMinAirPressure()))
                logger.log(Level.SEVERE, person.getName() + " has insufficient air pressure.");
            if (requireTemperature(support, config.getMinTemperature(), config.getMaxTemperature()))
                logger.log(Level.SEVERE, person.getName() + " cannot survive long at this high/low temperature.");
        }
        catch (Exception e) {
            logger.log(Level.SEVERE,person.getName() + " - Error in lifesupport needs: " + e.getMessage());
        }

        // Build up fatigue & hunger for given time passing.
        setFatigue(fatigue + time);
        setHunger(hunger + time);

        // Add time to all medications affecting the person.
        Iterator<Medication> i = medicationList.iterator();
        while (i.hasNext()) {
            Medication med = i.next();
            med.timePassing(time);
            if (!med.isMedicated()) {
                i.remove();
            }
        }

        // If person is at maximum stress, check for mental breakdown.
        if (stress == 100.0D) {
            checkForStressBreakdown(config, time);
        }

        // Calculate performance and most serious illness.
        recalculate();

        if (illnessEvent) {
            person.fireUnitUpdate(UnitEventType.ILLNESS_EVENT);
        }

        return (!isDead());
    }
    
    /**
     * Check for any random ailments that a person comes down with over a period of time.
     * @param time the time period (millisols).
     * @return list of ailments occurring.  May be empty.
     */
    private List<Complaint> checkForRandomAilments(double time) {
        
        List<Complaint> result = new ArrayList<Complaint>(0);
        
        // Check each possible medical complaint.
        Iterator<Complaint> i = getMedicalManager().getAllMedicalComplaints().iterator();
        while (i.hasNext()) {
            Complaint complaint = i.next();
            double probability = complaint.getProbability();
            
            // Check that medical complaint has a probability > zero.
            if (probability > 0D) {
                
                // Check that person does not already have a health problem with this complaint.
                if (!problems.containsKey(complaint)) {
                    
                    // Randomly determine if person suffers from ailment.
                    double chance = RandomUtil.getRandomDouble(100D);
                    double timeModifier = time / RANDOM_AILMENT_PROBABILITY_TIME;
                    if (chance <= (probability) * timeModifier) {
                        logger.info(person + " comes down with ailment " + complaint);
                        addMedicalComplaint(complaint);
                        result.add(complaint);
                    }
                }
            }
        }
        
        return result;
    }

    /** Adds a new medical complaint to the person.
     *  @param complaint the new medical complaint
     */
    public void addMedicalComplaint(Complaint complaint) {
        if ((complaint != null) && !problems.containsKey(complaint)) {
            HealthProblem problem = new HealthProblem(complaint, person);
            problems.put(complaint, problem);
            recalculate();
        }
    }
    
    // 2014-11-28 Added consumeDessert()
    public void consumeDessert(double amount, Unit container) {
        if (container == null) throw new IllegalArgumentException("container is null");
	
		AmountResource soymilkAR = AmountResource.findAmountResource("Soymilk");
	
		double foodEaten = amount;
		double soymilkAvailable = container.getInventory().getAmountResourceStored(soymilkAR, false);

		//System.out.println("PhysicalCondition : " + container.getName() + " has " + soymilkAvailable + " kg soymilk. ");
		
		if (soymilkAvailable < 0.01D) {
			throw new IllegalStateException( container.getName() + " has " + " very little soymilk remaining!");
		}
		else {
			// if container has less than enough food, finish up all food in the container
			if (foodEaten > soymilkAvailable)
				foodEaten = soymilkAvailable;
	
			foodEaten = Math.round(foodEaten * 1000000.0) / 1000000.0;
			// subtract food from container
			container.getInventory().retrieveAmountResource(soymilkAR, foodEaten);	
			//System.out.println("PhysicalCondition : " + foodEaten + " kg of soymilk was just consumed");
		}
    }
   
    
    /**
     * Person consumes given amount of food
     * @param amount amount of food to consume (in kg).
     * @param container unit to get food from
     * @throws Exception if error consuming food.
     */
	// 2014-11-06 Toss a dice to decide what food category to eat
	// Spice Group is excluded from the selection
    // 2014-11-28 Temporarily disable selection of other fresh food
    public void consumeFood(double amount, Unit container) {
        if (container == null) throw new IllegalArgumentException("container is null");
		consumePackedFood(amount, container, LifeSupport.FOOD);
 
    }
    
    /**
     * Person consumes given amount of packed food
     * @param amount amount of food to consume (in kg).
     * @param container unit to get food from
     * @throws Exception if error consuming food.  
     */
	// 2014-11-07 Added consumePackedFood()
    	public void consumePackedFood(double amount, Unit container, String foodType) {
	    	AmountResource food = AmountResource.findAmountResource(foodType);
            double foodEaten = amount;
            double foodAvailable = container.getInventory().getAmountResourceStored(food, false);

            if (foodAvailable < 0.01D) {
                throw new IllegalStateException("Warning: less than 0.01 kg dried food remaining!");   
            }
            // if container has less than enough food, finish up all food in the container
            else { 
            	
            	if (foodEaten > foodAvailable)
            		foodEaten = foodAvailable;
            
            	foodEaten = Math.round(foodEaten * 1000000.0) / 1000000.0;
            	// subtract food from container
            	container.getInventory().retrieveAmountResource(food, foodEaten);
    			//System.out.println("PhysicalCondition : " + foodEaten + " kg of dried food was just consumed");
            	//logger.info("consumePackedFood() : food Eaten : "
            	//	+ foodEaten +  ", food remaining : " + (foodAvailable-foodEaten));
            }
    }

    /**
     * Person consumes a given amount of food not taken from local container.
     * @param amount the amount of food to consume (in kg).
     */
    public void consumeFood(double amount) {
        //System.out.println("PhysicalCondition.java : just called consumeFood(double amount) : food NOT taken from local container. amount is " + amount);
        // if (checkResourceConsumption(amount, amount, MIN_VALUE, getMedicalManager().getStarvation()))
        // 	recalculate();
    }

    /**
     * Person consumes given amount of oxygen
     * @param support Life support system providing oxygen.
     * @param amount amount of oxygen to consume (in kg)
     * @return new problem added.
     * @throws Exception if error consuming oxygen.
     */
    private boolean consumeOxygen(LifeSupport support, double amount) {
        double amountRecieved = support.provideOxygen(amount);

        return checkResourceConsumption(amountRecieved, amount / 2D,
                MIN_VALUE, getMedicalManager().getSuffocation());
    }

    /**
     * Person consumes given amount of water
     * @param support Life support system providing water.
     * @param amount amount of water to consume (in kg)
     * @return new problem added.
     * @throws Exception if error consuming water.
     */
    private boolean consumeWater(LifeSupport support, double amount) {
        double amountReceived = support.provideWater(amount);

        return checkResourceConsumption(amountReceived, amount / 2D,
                MIN_VALUE, getMedicalManager().getDehydration());
    }

    /**
     * This method checks the consume values of a resource. If the
     * actual is less than the required then a HealthProblem is
     * generated. If the required amount is satisfied, then any problem
     * is recovered.
     *
     * @param actual The amount of resource provided.
     * @param require The amount of resource required.
     * @param complaint Problem associated to this resource.
     * @return Has a new problem been added.
     */
    private boolean checkResourceConsumption(double actual, double required,
            int bounds, Complaint complaint) {

        boolean newProblem = false;
        if ((bounds == MIN_VALUE) && (actual < required)) newProblem = true;
        if ((bounds == MAX_VALUE) && (actual > required)) newProblem = true;

        if (newProblem) {
            addMedicalComplaint(complaint);
            person.fireUnitUpdate(UnitEventType.ILLNESS_EVENT);
        }
        else {
            //Is the person suffering from the illness, if so recovery
            // as the amount has been provided
            HealthProblem illness = problems.get(complaint);
            if (illness != null) {
                illness.startRecovery();
                person.fireUnitUpdate(UnitEventType.ILLNESS_EVENT);
            }
        }
        return newProblem;
    }

    /**
     * Person requires minimum air pressure.
     * @param support Life support system providing air pressure.
     * @param pressure minimum air pressure person requires (in Pa)
     * @return new problem added.
     */
    private boolean requireAirPressure(LifeSupport support, double pressure) {
        return checkResourceConsumption(support.getAirPressure(), pressure,
                MIN_VALUE, getMedicalManager().getDecompression());
    }

    /**
     * Person requires minimum temperature.
     * @param support Life support system providing temperature.
     * @param temperature minimum temperature person requires (in degrees Celsius)
     * @return new problem added.
     */
    private boolean requireTemperature(LifeSupport support, double minTemperature,
            double maxTemperature) {

        boolean freeze = checkResourceConsumption(support.getTemperature(),
                minTemperature, MIN_VALUE, getMedicalManager().getFreezing());
        boolean hot = checkResourceConsumption(support.getTemperature(),
                maxTemperature, MAX_VALUE, getMedicalManager().getHeatStroke());
        return freeze || hot;
    }

    /**
     * Get the details of this Person's death.
     * @return Detail of the death, will be null if person is still alive.
     */
    public DeathInfo getDeathDetails() {
        return deathDetails;
    }

    /** Gets the person's fatigue level
     *  @return person's fatigue
     */
    public double getFatigue() {
        return fatigue;
    }

    /**
     * Get the performance factor that effect Person with the complaint.
     * @return The value is between 0 -> 1.
     */
    public double getPerformanceFactor() {
        return performance;
    }

    /**
     * Sets the performance factor.
     * @param newPerformance new performance (between 0 and 1).
     */
    private void setPerformanceFactor(double newPerformance) {
        if (newPerformance != performance) {
            performance = newPerformance;
            person.fireUnitUpdate(UnitEventType.PERFORMANCE_EVENT);
        }
    }

    /**
     * Gets the person with this physical condition
     * @return
     */
    Person getPerson() {
        return person;
    }

    /**
     * Define the fatigue setting for this person
     * @param newFatigue New fatigue.
     */
    public void setFatigue(double newFatigue) {
        if (fatigue != newFatigue) {
            fatigue = newFatigue;
            person.fireUnitUpdate(UnitEventType.FATIGUE_EVENT);
        }
    }

    /** Gets the person's hunger level
     *  @return person's hunger
     */
    public double getHunger() {
        return hunger;
    }
    
    /**
     * Define the hunger setting for this person
     * @param newHunger New hunger.
     */
    public void setHunger(double newHunger) {
        if (hunger != newHunger) {
            hunger = newHunger;

            PersonConfig personConfig = SimulationConfig.instance().getPersonConfiguration();
            double starvationTime = 0D;
            try {
                starvationTime = personConfig.getStarvationStartTime() * 1000D;
            }
            catch (Exception e) {
                e.printStackTrace(System.err);
            }

            Complaint starvation = getMedicalManager().getStarvation();
            if (hunger > starvationTime) {
                if (!problems.containsKey(starvation)) {
                    addMedicalComplaint(starvation);
                    person.fireUnitUpdate(UnitEventType.ILLNESS_EVENT);
                }
            }
            else if (hunger == 0D) {
                HealthProblem illness = problems.get(starvation);
                if (illness != null) {
                    illness.startRecovery();
                    person.fireUnitUpdate(UnitEventType.ILLNESS_EVENT);
                }
            }

            person.fireUnitUpdate(UnitEventType.HUNGER_EVENT);
        }
    }

    /**
     * Gets the person's stress level
     * @return stress (0.0 to 100.0)
     */
    public double getStress() {
        return stress;
    }

    /**
     * Sets the person's stress level.
     * @param newStress the new stress level (0.0 to 100.0)
     */
    public void setStress(double newStress) {
        if (stress != newStress) {
            stress = newStress;
            if (stress > 100D) stress = 100D;
            else if (stress < 0D) stress = 0D;
            else if (Double.isNaN(stress)) stress = 0D;
            person.fireUnitUpdate(UnitEventType.STRESS_EVENT);
        }
    }

    /**
     * Checks if person has an anxiety attack due to too much stress.
     * @param config the person configuration.
     * @param time the time passing (millisols)
     */
    private void checkForStressBreakdown(PersonConfig config, double time) {
        try {
            if (!problems.containsKey(ANXIETY_ATTACK)) {

                // Determine stress resilience modifier (0D - 2D).
                int resilience = person.getNaturalAttributeManager().getAttribute(NaturalAttribute.STRESS_RESILIENCE);
                double resilienceModifier = (double) (100 - resilience) / 50D;

                // If random breakdown, add anxiety attack.
                if (RandomUtil.lessThanRandPercent(config.getStressBreakdownChance() * time * resilienceModifier)) {
                    Complaint anxietyAttack = getMedicalManager().getComplaintByName(ANXIETY_ATTACK);
                    if (anxietyAttack != null) {
                        addMedicalComplaint(anxietyAttack);
                        person.fireUnitUpdate(UnitEventType.ILLNESS_EVENT);
                        logger.info(person.getName() + " has an anxiety attack.");
                    }
                    else logger.log(Level.SEVERE,"Could not find 'Anxiety Attack' medical complaint in 'conf/medical.xml'");
                }
            }
        }
        catch (Exception e) {
            logger.log(Level.SEVERE,"Problem reading 'stress-breakdown-chance' element in 'conf/people.xml': " + e.getMessage());
        }
    }

    /**
     * This Person is now dead.
     * @param illness The compliant that makes person dead.
     */
    public void setDead(HealthProblem illness) {
        setFatigue(0D);
        setHunger(0D);
        setPerformanceFactor(0D);
        setStress(0D);
        alive = false;

        deathDetails = new DeathInfo(person);

        logger.severe(person + " dies due to " + illness);

        // Create medical event for death.
        MedicalEvent event = new MedicalEvent(person, illness, EventType.MEDICAL_DEATH);
        Simulation.instance().getEventManager().registerNewEvent(event);

        // Throw unit event.
        person.fireUnitUpdate(UnitEventType.DEATH_EVENT);
    }

    /**
     * Checks if the person is dead.
     *
     * @return true if dead
     */
    public boolean isDead() {
        return !alive;
    }

    /**
     * Get a string description of the most serious health situation.
     * @return A string containing the current illness if any.
     */
    public String getHealthSituation() {
        String situation = "Well";
        if (serious != null) {
            if (isDead()) {
                situation = "Dead, " + serious.getIllness().getName();
            }
            else {
                situation = serious.getSituation();
            }
            //else situation = "Not Well";
        }
        return situation;
    }

    /**
     * Gets the most serious illness.
     * @return most serious illness
     */
    public Complaint getMostSerious() {
        return serious.getIllness();
    }

    /**
     * The collection of known Medical Problems.
     */
    public Collection<HealthProblem> getProblems() {
        return problems.values();
    }

    /**
     * Calculate the most serious problem and the person's performance.
     */
    private void recalculate() {

        double tempPerformance = 1.0D;
        serious = null;

        // Check the existing problems. find most serious & performance
        // effecting
        Iterator<HealthProblem> iter = problems.values().iterator();
        while(iter.hasNext()) {
            HealthProblem problem = iter.next();
            double factor = problem.getPerformanceFactor();
            if (factor < tempPerformance) tempPerformance = factor;

            if ((serious == null) || (serious.getIllness().getSeriousness() <
                    problem.getIllness().getSeriousness())) {
                serious = problem;
            }
        }

        // TODO: change health status as well in all of the following cases
        // High fatigue reduces performance.
        //if (fatigue > 1000D) tempPerformance -= (fatigue - 1000D) * .0003D;
        if (fatigue > 1000D) tempPerformance -= (fatigue - 1000D) * .003D;

        // High hunger reduces performance.
        //if (hunger > 1000D) tempPerformance -= (hunger - 1000D) * .0001D;
        if (hunger > 1000D) tempPerformance -= (hunger - 1000D) * .001D;
        
        // High stress reduces performance.
        //if (stress >= 80D) tempPerformance -= (stress - 80D) * .02D;
        if (stress >= 80D) tempPerformance -= (stress - 80D) * .04D;
        
        if (tempPerformance < 0D) tempPerformance = 0D;

        setPerformanceFactor(tempPerformance);
    }

    /**
     * Checks if the person has any serious medical problems.
     * @return true if serious medical problems
     */
    public boolean hasSeriousMedicalProblems() {
        boolean result = false;
        Iterator<HealthProblem> meds = getProblems().iterator();
        while (meds.hasNext()) {
            if (meds.next().getIllness().getSeriousness() >= 50) result = true;
        }
        return result;
    }

    /**
     * Gets the oxygen consumption rate per Sol.
     * @return oxygen consumed (kg/Sol)
     * @throws Exception if error in configuration.
     */
    public static double getOxygenConsumptionRate() {
        PersonConfig config = SimulationConfig.instance().getPersonConfiguration();
        return config.getOxygenConsumptionRate();
    }

    /**
     * Gets the water consumption rate per Sol.
     * @return water consumed (kg/Sol)
     * @throws Exception if error in configuration.
     */
    public static double getWaterConsumptionRate() {
        PersonConfig config = SimulationConfig.instance().getPersonConfiguration();
        return config.getWaterConsumptionRate();
    }

    /**
     * Gets the food consumption rate per Sol.
     * @return food consumed (kg/Sol)
     * @throws Exception if error in configuration.
     */
    public static double getFoodConsumptionRate() {
        PersonConfig config = SimulationConfig.instance().getPersonConfiguration();
        return config.getFoodConsumptionRate();
    }

    /**
     * Gets a list of medication affecting the person.
     * @return list of medication.
     */
    public List<Medication> getMedicationList() {
        return new ArrayList<Medication>(medicationList);
    }

    /**
     * Checks if the person is affected by the given medication.
     * @param medicationName the name of the medication.
     * @return true if person is affected by it.
     */
    public boolean hasMedication(String medicationName) {
        if (medicationName == null)
            throw new IllegalArgumentException("medicationName is null");

        boolean result = false;

        Iterator<Medication> i = medicationList.iterator();
        while (i.hasNext()) {
            if (medicationName.equals(i.next().getName())) result = true;
        }

        return result;
    }

    /**
     * Adds a medication that affects the person.
     * @param medication the medication to add.
     */
    public void addMedication(Medication medication) {
        if (medication == null)
            throw new IllegalArgumentException("medication is null");
        medicationList.add(medication);
    }

    /**
     * Prepare object for garbage collection.
     */
    public void destroy() {
        deathDetails = null;
        problems.clear();
        problems = null;
        serious = null;
        person = null;
        medicationList.clear();
        medicationList = null;
    }
}
