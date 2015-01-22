package nars.logic;

import nars.event.AbstractReaction;
import nars.core.Events;
import nars.core.Events.CycleEnd;
import nars.core.Events.CycleStart;
import nars.core.Memory;
import nars.core.NAR;
import nars.logic.entity.Concept;
import nars.logic.entity.Task;

public abstract class MemoryObserver extends AbstractReaction {

    private final Memory memory;

    public MemoryObserver(NAR n, boolean active) {
        this(n.memory, active);
    }

    public MemoryObserver(Memory m, boolean active) {
        super(m.event, active,
                Events.CycleStart.class,
                Events.CycleEnd.class,
                Events.ConceptNew.class,
                Events.ConceptForget.class,
                Events.ConceptBeliefAdd.class,
                Events.ConceptBeliefRemove.class,
                Events.ConceptDirectProcessedTask.class,
                Events.ConceptFired.class,
                Events.ConceptGoalAdd.class,
                Events.ConceptGoalRemove.class,
                Events.ConceptQuestionAdd.class,
                Events.ConceptQuestionRemove.class,
                Events.ConceptUnification.class,
                Events.BeliefSelect.class,
                Events.PluginsChange.class,
                Events.TaskAdd.class,
                Events.TaskRemove.class,
                Events.TaskDerive.class,
                Events.TaskImmediateProcessed.class,
                Events.TermLinkSelected.class,
                Events.TermLinkTransform.class,
                //Events.UnExecutedGoal.class,

                //Output.OUT.class, 

                Events.ResetEnd.class);
        this.memory = m;
    }

    @Override
    public void event(final Class event, final Object[] arguments) {
        if (event == Events.ConceptNew.class) {
            onConceptAdd((Concept) arguments[0]);
        } else if (event == Events.OUT.class) {
            output(event, arguments[0].toString());
        } else if (event == Events.ResetEnd.class) {
            output(event);        
        } else if (event == CycleStart.class) {
            onCycleStart(memory.time());
        } else if (event == CycleEnd.class) {
            onCycleEnd(memory.time());
        } else {
            output(event, arguments);
        }

        //cycle start
        //cycle end
        //task add
        //task remove
    }

    /**
     * Add new text to display
     *
     * @param s The line to be displayed
     */
    abstract public void output(Class channel, Object... args);

    public void output(String s) {
        output(String.class, s);
    }

    /**
     * when a concept is instantiated
     */
    abstract public void onConceptAdd(Concept concept);

    /**
     * called at the beginning of each logic clock cycle
     */
    abstract public void onCycleStart(long clock);

    /**
     * called at the end of each logic clock cycle
     */
    abstract public void onCycleEnd(long clock);

    /**
     * Added task
     */
    abstract public void onTaskAdd(Task task, String reason);

    /**
     * Neglected task
     */
    abstract public void onTaskRemove(Task task, String reason);

}
