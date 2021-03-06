package net.sf.rails.game;

/** Abstract class for rounds that cannot be subclassed for one of the
 * other Round subclasses because UI is switchable: in some steps,
 * an SR-type UI and in other steps an OR-type UI should be displayed.
 */
public abstract class SwitchableUIRound extends Round {

    protected SwitchableUIRound(GameManager parent, String id) {
        super(parent, id);
    }

}
