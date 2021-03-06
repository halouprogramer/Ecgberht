package ecgberht.BehaviourTrees.Scouting;

import ecgberht.GameState;
import ecgberht.Squad;
import org.iaie.btree.state.State;
import org.iaie.btree.task.leaf.Action;
import org.iaie.btree.util.GameHandler;
import org.openbw.bwapi4j.unit.Unit;
import org.openbw.bwapi4j.unit.Worker;
import org.openbw.bwapi4j.unit.Wraith;

public class ChooseScout extends Action {

    public ChooseScout(String name, GameHandler gh) {
        super(name, gh);
    }

    @Override
    public State execute() {
        try {
            if (((GameState) this.handler).strat.name.equals("PlasmaWraithHell")) {
                for (Squad s : ((GameState) this.handler).sqManager.squads.values()) {
                    for (Unit u : s.members) {
                        if (u instanceof Wraith) {
                            ((GameState) this.handler).chosenScout = u;
                            s.members.remove(u);
                            return State.SUCCESS;
                        }
                    }
                }
            }
            if (!((GameState) this.handler).workerIdle.isEmpty()) {
                Worker chosen = ((GameState) this.handler).workerIdle.iterator().next();
                ((GameState) this.handler).chosenScout = chosen;
                ((GameState) this.handler).workerIdle.remove(chosen);
            }
            if (((GameState) this.handler).chosenScout == null) {
                for (Worker u : ((GameState) this.handler).workerMining.keySet()) {
                    if (!u.isCarryingMinerals()) {
                        ((GameState) this.handler).chosenScout = u;
                        ((GameState) this.handler).workerMining.remove(u);
                        break;
                    }
                }
            }
            if (((GameState) this.handler).chosenScout != null) return State.SUCCESS;
            return State.FAILURE;
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName());
            e.printStackTrace();
            return State.ERROR;
        }
    }
}
