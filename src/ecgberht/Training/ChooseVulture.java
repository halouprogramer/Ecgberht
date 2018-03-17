package ecgberht.Training;

import org.iaie.btree.state.State;
import org.iaie.btree.task.leaf.Action;
import org.iaie.btree.util.GameHandler;

import bwapi.Unit;
import bwapi.UnitType;
import ecgberht.GameState;


public class ChooseVulture extends Action {

	public ChooseVulture(String name, GameHandler gh) {
		super(name, gh);
	}

	@Override
	public State execute() {
		try {
			if(!((GameState)this.handler).Fs.isEmpty()) {
				if(((GameState)this.handler).getPlayer().allUnitCount(UnitType.Terran_Vulture) * 2 <= ((GameState)this.handler).getPlayer().allUnitCount(UnitType.Terran_Siege_Tank_Siege_Mode) + ((GameState)this.handler).getPlayer().allUnitCount(UnitType.Terran_Siege_Tank_Tank_Mode) + 2) {	
					for(Unit b:((GameState)this.handler).Fs) {
						if(!b.isTraining() && b.canTrain()) {
							((GameState)this.handler).chosenUnit = UnitType.Terran_Vulture;
							((GameState)this.handler).chosenBuilding = b;
							return State.SUCCESS;
						}
					}
				}
			}
			return State.FAILURE;
		} catch(Exception e) {
			System.err.println(this.getClass().getSimpleName());
			System.err.println(e);
			return State.ERROR;
		}
	}
}