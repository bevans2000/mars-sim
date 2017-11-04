/**
 * Mars Simulation Project
 * ThermalSystem.java
 * @version 3.1.0 2017-11-04
 * @author Manny Kung
 */
package org.mars_sim.msp.core.structure;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mars_sim.msp.core.Msg;
import org.mars_sim.msp.core.UnitEventType;
import org.mars_sim.msp.core.structure.building.Building;
import org.mars_sim.msp.core.structure.building.BuildingException;
import org.mars_sim.msp.core.structure.building.BuildingManager;
import org.mars_sim.msp.core.structure.building.function.FunctionType;
import org.mars_sim.msp.core.structure.building.function.ThermalGeneration;


/**
 * The ThermalSystem class is the settlement's Thermal Control, Distribution and Storage Subsystem.
 * This class will only have one and only one instance
 */
public class ThermalSystem
implements Serializable {

	/** default serial id. */
	private static final long serialVersionUID = 1L;

	/** default logger. */
	private static Logger logger = Logger.getLogger(ThermalSystem.class.getName());

	DecimalFormat fmt = new DecimalFormat("#.####");

	// Data members
	private double powerGeneratedCache;
	
	private double heatGeneratedCache;

	private double heatStored;

	private double heatRequired;
	
	private double heatValue;

	//private boolean sufficientHeat;

	private Settlement settlement;

	private BuildingManager manager;
	
	//private ThermalGeneration heater;

	/**
	 * Constructor.
	 */
	public ThermalSystem(Settlement settlement) {

		this.settlement = settlement;
		this.manager = settlement.getBuildingManager();
		
		heatGeneratedCache = 0D;
		heatStored = 0D;

		heatRequired = 0D;
		//sufficientHeat = true;

	}


	
	/**
	 * Gets the total max possible generated heat in the heating system.
	 * @return heat in kW
	 */
	public double getGeneratedHeat() {
		//logger.info("getGeneratedHeat() : heatGenerated is " + fmt.format(heatGenerated) );
		return heatGeneratedCache;
	}

	/**
	 * Gets the total max possible generated heat in the heating system.
	 * @return heat in kW
	 */
	public double getGeneratedPower() {
		return powerGeneratedCache;
	}


	/**
	 * Sets the new amount of generated heat in the heating system.
	 * @param newGeneratedHeat the new generated heat kW
	 */
	private void setGeneratedHeat(double newGeneratedHeat) {
		if (heatGeneratedCache != newGeneratedHeat) {
			heatGeneratedCache = newGeneratedHeat;
			settlement.fireUnitUpdate(UnitEventType.GENERATED_HEAT_EVENT);
		}
	}

	/**
	 * Sets the new amount of generated power in the heating system.
	 * @param newGeneratedHeat the new generated power kW
	 */
	private void setGeneratedPower(double newGeneratedPower) {
		if (powerGeneratedCache != newGeneratedPower) {
			powerGeneratedCache = newGeneratedPower;
			settlement.fireUnitUpdate(UnitEventType.GENERATED_POWER_EVENT);
		}
	}

	/**
	 * Gets the heat required from the heating system.
	 * @return heat in kJ/s
	 */
	// NOT USED FOR THE TIME BEING. always return ZERO
	public double getRequiredHeat() {
		return heatRequired;
	}

	/**
	 * Time passing for heating system.
	 * @param time amount of time passing (in millisols)
	 */
	public void timePassing(double time) {

		if(logger.isLoggable(Level.FINE)) {
			logger.fine(
				Msg.getString(
					"ThermalSystem.log.settlementHeatSituation",
					settlement.getName()
				)
			);
		}

	
		// update the total heat generated in the heating system.
		updateTotalHeatGenerated();

		updateTotalPowerGenerated();

		// Update heat value.
		determineHeatValue();

	}

	/**
	 * Updates the total heat generated in the heating system.
	 * @throws BuildingException if error determining total heat generated.
	 */
	private void updateTotalHeatGenerated() {
		double heat = 0D;

		// Add the heat generated by all heat generation buildings.
		//BuildingManager manager = settlement.getBuildingManager();
		Iterator<Building> iHeat = manager.getBuildings(FunctionType.THERMAL_GENERATION).iterator();
		while (iHeat.hasNext()) {
			Building b = iHeat.next();
			ThermalGeneration gen = b.getThermalGeneration();
			heat += gen.getGeneratedHeat();//b.getThermalGeneration().getGeneratedHeat();
			// logger.info(((Building) gen).getName() + " generated: " + gen.getGeneratedHeat());
		}
		setGeneratedHeat(heat);

		if(logger.isLoggable(Level.FINE)) {
			logger.fine(
				Msg.getString(
					"ThermalSystem.log.totalHeatGenerated", //$NON-NLS-1$
					Double.toString(heat)
				)
			);
		}
	}


	/**
	 * Updates the total power generated by the solar heat engine system.
	 * @throws BuildingException if error determining total heat generated.
	 */
	private void updateTotalPowerGenerated() {
		double power = 0D;

		// Add the heat generated by all heat generation buildings.
		//BuildingManager manager = settlement.getBuildingManager();
		Iterator<Building> i = manager.getBuildings(FunctionType.POWER_GENERATION).iterator();
		while (i.hasNext()) {
			Building b = i.next();
			ThermalGeneration gen = b.getThermalGeneration();
			if (gen == null)
				break;
			power += b.getThermalGeneration().getGeneratedPower();//.getPowerGeneration().getGeneratedPower();
			//System.out.println(building.getNickName() + "'s power : " + power);
			// logger.info(((Building) gen).getName() + " generated: " + gen.getGeneratedHeat());
		}
		setGeneratedPower(power);
		//System.out.println("total power : " + power);

		if(logger.isLoggable(Level.FINE)) {
			logger.fine(
				Msg.getString(
					"ThermalSystem.log.totalPowerGenerated", //$NON-NLS-1$
					Double.toString(power)
				)
			);
		}
	}

	

	/**
	 * Determines the value of heat energy at the settlement.
	 */
	private void determineHeatValue() {
		double demand = heatRequired;
		double supply = heatGeneratedCache + (heatStored / 2D);

		double newHeatValue = demand / (supply + 1.0D);

		if (newHeatValue != heatValue) {
			heatValue = newHeatValue;
			settlement.fireUnitUpdate(UnitEventType.HEAT_VALUE_EVENT);
		}
	}

	/**
	 * Prepare object for garbage collection.
	 */
	public void destroy() {
		manager = null;
		settlement = null;
	}
}