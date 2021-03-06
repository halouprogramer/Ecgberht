package ecgberht.Agents;

import bwem.Base;
import ecgberht.EnemyBuilding;
import ecgberht.Simulation.SimInfo;
import ecgberht.Util.MutablePair;
import ecgberht.Util.Util;
import org.openbw.bwapi4j.Position;
import org.openbw.bwapi4j.type.Order;
import org.openbw.bwapi4j.type.UnitType;
import org.openbw.bwapi4j.unit.*;

import java.util.Objects;

import static ecgberht.Ecgberht.getGs;

public class VultureAgent extends Agent implements Comparable<Unit> {

    public Vulture unit;
    private int mines = 3;
    private UnitType type = UnitType.Terran_Vulture;
    private int lastPatrolFrame = 0;

    public VultureAgent(Unit unit) {
        super();
        this.unit = (Vulture) unit;
        this.myUnit = unit;
    }

    public void placeMine(Position pos) {
        if (mines > 0) unit.spiderMine(pos);
    }

    @Override
    public boolean runAgent() {
        try {
            if (unit.getHitPoints() <= 20) {
                MutablePair<Base, Unit> cc = getGs().mainCC;
                if (cc != null && cc.second != null) {
                    Position ccPos = cc.second.getPosition();
                    if (getGs().getGame().getBWMap().isValidPosition(ccPos)) {
                        unit.move(ccPos);
                        getGs().myArmy.add(unit);
                        return true;
                    }
                }
                unit.move(getGs().getPlayer().getStartLocation().toPosition());
                getGs().myArmy.add(unit);
                return true;
            }
            actualFrame = getGs().frameCount;
            frameLastOrder = unit.getLastCommandFrame();
            closeEnemies.clear();
            mainTargets.clear();
            if (frameLastOrder == actualFrame) return false;
            //Status old = status;
            getNewStatus();
            //if (old == status && status != Status.COMBAT && status != Status.ATTACK) return false;
            if (status != Status.COMBAT && status != Status.PATROL) attackUnit = null;
            if ((status == Status.ATTACK || status == Status.IDLE) && (unit.isIdle() || unit.getOrder() == Order.PlayerGuard)) {
                Position pos = Util.chooseAttackPosition(unit.getPosition(), false);
                Position target = unit.getOrderTargetPosition();
                if (pos != null && getGs().getGame().getBWMap().isValidPosition(pos) && (target == null || !target.equals(pos))) {
                    unit.move(pos);
                    status = Status.ATTACK;
                    return false;
                }
            }
            switch (status) {
                case ATTACK:
                    attack();
                    break;
                case COMBAT:
                    combat();
                    break;
                case KITE:
                    kite();
                    break;
                case RETREAT:
                    retreat();
                    break;
                case PATROL:
                    patrol();
                    break;
            }
            return false;
        } catch (Exception e) {
            System.err.println("Exception VultureAgent");
            e.printStackTrace();
        }
        return false;
    }

    private void patrol() {
        if (unit.getOrder() == Order.Patrol) return;
        if (attackUnit != null && unit.getOrder() != Order.Patrol) {
            Position pos = Util.choosePatrolPositionVulture(unit, attackUnit);
            if (pos != null && getGs().getGame().getBWMap().isValidPosition(pos)) {
                unit.patrol(pos);
                attackUnit = null;
                lastPatrolFrame = actualFrame;
                return;
            }
        }
        attackUnit = null;
    }

    private void combat() {
        Unit toAttack = getUnitToAttack(unit, closeEnemies);
        if (toAttack != null) {
            if (attackUnit != null && attackUnit.equals(toAttack)) return;
            unit.attack(toAttack);
            attackUnit = toAttack;
        } else if (!mainTargets.isEmpty()) {
            toAttack = getUnitToAttack(unit, mainTargets);
            if (toAttack != null && attackUnit != null && !attackUnit.equals(toAttack)) {
                unit.attack(toAttack);
                attackUnit = toAttack;
                attackPos = null;
            }
        }
    }

    private void getNewStatus() {
        Position myPos = unit.getPosition();
        if (getGs().enemyCombatUnitMemory.isEmpty()) {
            status = Status.ATTACK;
            return;
        }
        for (Unit u : getGs().enemyCombatUnitMemory) {
            if (u instanceof Worker && !((PlayerUnit) u).isAttacking()) mainTargets.add(u);
            if (Util.broodWarDistance(u.getPosition(), myPos) <= 600) closeEnemies.add(u);
        }
        for (EnemyBuilding u : getGs().enemyBuildingMemory.values()) {
            if ((u.type.canAttack() || u.type == UnitType.Terran_Bunker) && u.unit.isCompleted()) {
                if (Util.broodWarDistance(myPos, u.pos.toPosition()) <= 600) closeEnemies.add(u.unit);
            }
        }
        if (closeEnemies.isEmpty()) status = Status.ATTACK;
        else {
            boolean meleeOnly = checkOnlyMelees();
            if (!meleeOnly && getGs().sim.getSimulation(unit, SimInfo.SimType.GROUND).lose) {
                status = Status.RETREAT;
                return;
            }
            if (status == Status.PATROL && actualFrame - lastPatrolFrame > 5) {
                status = Status.COMBAT;
                return;
            }
            int cd = unit.getGroundWeapon().cooldown();
            Unit closestAttacker = Util.getClosestUnit(unit, closeEnemies);
            if (closestAttacker != null && (cd != 0 || closestAttacker.getDistance(unit) < unit.getGroundWeaponMaxRange() * 0.6)) {
                status = Status.KITE;
                return;
            }
            if (status == Status.COMBAT || status == Status.ATTACK) {
                if (attackUnit != null) {
                    int weaponRange = attackUnit instanceof GroundAttacker ? ((GroundAttacker) attackUnit).getGroundWeaponMaxRange() : 0;
                    if (weaponRange > type.groundWeapon().maxRange()) return;
                }
                if (cd > 0) {
                    attackUnit = null;
                    attackPos = null;
                    status = Status.KITE;
                    return;
                }
            }
            if (status == Status.KITE) {
                Unit closest = getUnitToAttack(unit, closeEnemies);
                if (closest != null) {
                    double dist = unit.getDistance(closest);
                    double speed = type.topSpeed();
                    double timeToEnter = 0.0;
                    if (speed > .00001) timeToEnter = Math.max(0.0, dist - type.groundWeapon().maxRange()) / speed;
                    if (timeToEnter >= cd) {
                        //status = Status.COMBAT;
                        status = Status.PATROL;
                        attackUnit = closest;
                        return;
                    }
                } else {
                    status = Status.ATTACK;
                    return;
                }
                if (cd == 0) status = Status.COMBAT;
            }
        }
    }

    private boolean checkOnlyMelees() {
        for (Unit e : closeEnemies) {
            int weaponRange = e instanceof GroundAttacker ? ((GroundAttacker) e).getGroundWeaponMaxRange() : 0;
            if (weaponRange > 32 || e instanceof Bunker) return false;
        }
        return true;
    }

    private void kite() {
        Position kite = getGs().kiteAway(unit, closeEnemies);
        if (!getGs().getGame().getBWMap().isValidPosition(kite) || kite.equals(unit.getPosition())) {
            retreat();
            return;
        }
        Position target = unit.getOrderTargetPosition();
        if (target != null && !target.equals(kite)) unit.move(kite);
        if (target == null) unit.move(kite);
    }

    private void attack() {
        if (unit.isAttackFrame()) return;
        attackPos = selectNewAttack();
        if (attackPos == null || !getGs().bw.getBWMap().isValidPosition(attackPos)) {
            attackUnit = null;
            attackPos = null;
            return;
        }
        if (getGs().bw.getBWMap().isValidPosition(attackPos)) {
            Position target = unit.getOrderTargetPosition();
            if (!attackPos.equals(target)) {
                unit.attack(attackPos);
                attackUnit = null;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof VultureAgent)) return false;
        VultureAgent vulture = (VultureAgent) o;
        return unit.equals(vulture.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unit);
    }

    @Override
    public int compareTo(Unit v1) {
        return this.unit.getId() - v1.getId();
    }
}
