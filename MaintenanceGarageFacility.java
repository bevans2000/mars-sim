//************************** Maintenance Garage Facility **************************
// Last Modified: 5/8/00

// The MaintenanceGarageFacility class represents the pressurized maintenance garage in a settlement.
// Vehicles can be taken to a maintenance garage for repair and maintenance.
// Note: Any number or size of vehicles can always be parked outside a settlement.  The garage's
// capacity only reflects those vehicles in the garage itself.

// A settlement may or may not have a maintenance garage.

import java.util.*;

public class MaintenanceGarageFacility extends Facility {

	// Data members
	
	private int maxVehicleSize;   // The maximum size of vehicle the garage can accomidate.
	private int maxSizeCapacity;  // The total size point sum of vehicles the garage can accomidate at any given time.
	private int currentSizeSum;   // The current sum of vehicle size points in the garage.
	private Vector vehicles;      // A list of vehicles currently in the garage.

	// Constructor for random creation.

	public MaintenanceGarageFacility(FacilityManager manager) {
	
		// Use Facility's constructor.
		
		super(manager, "Maintenance Garages", "Garage");
	
		// Initialize random maxVehicleSize from 2 to 5.
		
		maxVehicleSize = 2 + RandomUtil.getRandomInteger(3);
		
		// Initialize random maxSizeCapacity from maxVehicleSize to 5 X maxVehicleSize.
		
		maxSizeCapacity = maxVehicleSize + (RandomUtil.getRandomInteger(4 * maxVehicleSize));
	}
	
	// Constructor for set values (used later when facilities can be built or upgraded.)
	
	public MaintenanceGarageFacility(FacilityManager manager, int maxVehicleSize, int maxSizeCapacity) {
	
		// Use Facility's constructor.
		
		super(manager, "Maintenance Garage", "Garage");
		
		// Initialize data members.
		
		this.maxVehicleSize = maxVehicleSize;
		this.maxSizeCapacity = maxSizeCapacity;
	}
	
	// Returns the maximum vehicle size the garage can accomidate.
	
	public int getMaxVehicleSize() { return maxVehicleSize; }
	
	// Returns the total size point sum of vehicles the garage can accomidate at any given time.
	
	public int getMaxSizeCapacity() { return maxSizeCapacity; }
	
	// Add vehicle to garage if there's room.
	// Returns true if vehicle has been added successfully.
	// Returns false if vehicle could not be added.
	
	public boolean addVehicle(Vehicle vehicle) {
		
		int vehicleSize = vehicle.getSize();
		
		// If vehicle is within the size limitations of the garage, add it.
		
		if (vehicleSize <= maxVehicleSize) {
			if ((vehicleSize + currentSizeSum) <= maxSizeCapacity) {
				vehicles.addElement(vehicle);
				currentSizeSum += vehicleSize;
				return true;
			}
		}
		
		return false;
	}
	
	// Removes a vehicle from the garage.
	// If the vehicle is not in the garage, does nothing.
	
	public void removeVehicle(Vehicle vehicle) {
	
		if (vehicleInGarage(vehicle)) {
			vehicles.removeElement(vehicle);
			currentSizeSum -= vehicle.getSize();
		}
	}
	
	// Returns true if vehicle is currently in the garage.
	// Returns false otherwise.
	
	public boolean vehicleInGarage(Vehicle vehicle) {
		
		boolean result = false;
		
		for (int x=0; x < vehicles.size(); x++) {
			if (vehicle == vehicles.elementAt(x)) result = true;
		}
		
		return result;
	}
	
	// Returns an array of vehicle names of the vehicles currently in the garage.
	
	public String[] getVehicleNames() {
	
		String[] result = new String[vehicles.size()];
	
		for (int x=0; x < vehicles.size(); x++) result[x] = ((Vehicle) vehicles.elementAt(x)).getName();
		
		return result;
	}
	
	// Returns a vector of vehicles currently in the garage.
	
	public Vector getVehicles() {
		
		Vector result = new Vector();
		
		for (int x=0; x < vehicles.size(); x++) result.addElement(vehicles.elementAt(x));
		
		return result;
	}
}	

// Mars Simulation Project
// Copyright (C) 1999 Scott Davis
//
// For questions or comments on this project, contact:
//
// Scott Davis
// 1725 W. Timber Ridge Ln. #6206
// Oak Creek, WI  53154
// scud1@execpc.com
// http://www.execpc.com/~scud1/
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA