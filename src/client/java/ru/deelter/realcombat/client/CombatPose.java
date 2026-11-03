package ru.deelter.realcombat.client;

import ru.deelter.realcombat.combat.Dir;
import ru.deelter.realcombat.combat.Phase;

/**
 * What to draw for a fighter this frame.
 *
 * @param progress 0 at the start of the phase, 1 at its end, with the partial tick
 */
public record CombatPose(Phase phase, Dir dir, float progress, boolean riposte) {
}
