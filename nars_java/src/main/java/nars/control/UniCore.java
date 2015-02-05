package nars.control;

import com.nurkiewicz.typeof.TypeOf;
import nars.core.Core;
import nars.core.Events;
import nars.core.Memory;
import nars.core.Parameters;
import nars.logic.BudgetFunctions;
import nars.logic.FireConcept;
import nars.logic.entity.BudgetValue;
import nars.logic.entity.Concept;
import nars.logic.entity.ConceptBuilder;
import nars.logic.entity.Term;
import nars.util.bag.Bag;
import nars.util.bag.select.BagActivator;
import nars.util.bag.impl.CacheBag;
import nars.util.bag.impl.LevelBag;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base class for single-threaded Cores
 */
abstract public class UniCore implements Core {

    /* ---------- Long-term storage for multiple cycles ---------- */
    /**
     * Concept bag. Containing all Concepts of the system
     */
    public final Bag<Term, Concept> concepts;
    public final CacheBag<Term, Concept> subcon;
    protected final ConceptBuilder conceptBuilder;

    protected List<Runnable> run = new ArrayList();

    protected Memory memory;

    public UniCore(Bag<Term, Concept> concepts, CacheBag<Term, Concept> subcon, ConceptBuilder conceptBuilder) {

        this.concepts = concepts;
        this.subcon = subcon;
        this.conceptBuilder = conceptBuilder;

    }

    /** for removing a specific concept (if it's not putBack) */
    public Concept takeOut(Term t) {
        return concepts.TAKE(t);
    }

    @Override
    public void init(Memory m) {
        this.memory = m;
        if (concepts instanceof CoreAware)
            ((CoreAware)concepts).setCore(this);
        if (concepts instanceof Bag.MemoryAware)
            ((Bag.MemoryAware)concepts).setMemory(m);
    }

    protected static class DefaultFireConcept extends FireConcept {

        private final Bag<Term, Concept> bag;

        public DefaultFireConcept(Memory mem, Bag<Term, Concept> bag, Concept concept, int numTaskLinks) {
            super(mem, concept, numTaskLinks);
            this.bag = bag;
        }
        @Override
        public void beforeFinish() {

            float forgetCycles = memory.param.cycles(memory.param.conceptForgetDurations);
            bag.putBack(currentConcept, forgetCycles, memory);
        }
    }

    protected FireConcept newFireConcept(Concept c) {
        return new DefaultFireConcept(memory, concepts, c, 1);
    }

    protected FireConcept nextConcept() {
        Concept currentConcept = concepts.TAKENEXT();
        if (currentConcept==null)
            return null;

        return newFireConcept(currentConcept);
    }

    @Override
    public void reset() {
        concepts.clear();

    }


    public Iterable<Concept> getConcepts() {
        return concepts.values();
    }

    @Override
    public Concept concept(final Term term) {
        return concepts.GET(term);
    }

    @Override
    public void conceptRemoved(Concept c) {

        if (subcon!=null) {
            subcon.add(c);
            //System.out.println("forget: " + c + "   con=" + concepts.size() + " subcon=" + subcon.size());
        }
        else {
            memory.emit(Events.ConceptForget.class, c);


            //explicitly destroy all concept data structures to free memory for GC
            //c.end();
        }




    }

    public class ConceptActivator extends BagActivator<Term,Concept> {

        final float relativeThreshold = Parameters.FORGET_QUALITY_RELATIVE;

        private boolean createIfMissing;
        private long now;

        public Concept updateItem(Concept c) {

            long cyclesSinceLastForgotten = now - c.budget.getLastForgetTime();
            memory.forget(c, cyclesSinceLastForgotten, relativeThreshold);

            if (budget!=null) {
                BudgetValue cb = c.budget;
                BudgetFunctions.activate(cb, getBudget(), BudgetFunctions.Activating.TaskLink);
            }

            return c;
        }

        public ConceptActivator set(Term t, BudgetValue b, boolean createIfMissing, long now) {
            setKey(t);
            setBudget(b);
            this.createIfMissing = createIfMissing;
            this.now = now;
            return this;
        }

        @Override
        public Concept newItem() {

            //try remembering from subconscious
            if (subcon!=null) {
                Concept concept = subcon.take(getKey());
                if (concept!=null) {

                    //reset the forgetting period to zero so that its time while forgotten will not continue to penalize it during next forgetting iteration
                    concept.budget.setLastForgetTime(now);

                    memory.emit(Events.ConceptRemember.class, concept);

                    return concept;
                }
            }

            //create new concept, with the applied budget
            if (createIfMissing) {

                Concept concept = conceptBuilder.newConcept(budget, getKey(), memory);

                if (memory.logic!=null)
                    memory.logic.CONCEPT_NEW.hit();

                memory.emit(Events.ConceptNew.class, concept);

                return concept;
            }

            return null;
        }

        @Override
        public void overflow(Concept overflow) {
            conceptRemoved(overflow);
        }
    }

    final ConceptActivator activator = new ConceptActivator();


    @Override
    public Concept conceptualize(BudgetValue budget, final Term term, boolean createIfMissing) {

        return concepts.UPDATE( activator.set(term, budget, createIfMissing, memory.time()) );

    }


    @Override public void activate(final Concept c, final BudgetValue b, BudgetFunctions.Activating mode) {
        concepts.TAKE(c.name());
        BudgetFunctions.activate(c.budget, b, mode);
        concepts.putBack(c, memory.param.cycles(memory.param.conceptForgetDurations), memory);
    }

//    @Override
//    public void forget(Concept c) {
//        concepts.take(c.name());
//        concepts.putBack(c, memory.param.conceptForgetDurations.getCycles(), memory);
//    }

    @Override
    public Concept sampleNextConcept() {
        return concepts.PEEKNEXT();
    }

    @Override
    public Iterator<Concept> iterator() {
        return concepts.iterator();
    }

    @Override
    public Memory getMemory() {
        return memory;
    }


    @Override
    public void forEach(Consumer<? super Concept> action) {
        //use experimental consumer for levelbag to avoid allocating so many iterators within iterators
        TypeOf.whenTypeOf(concepts).is(LevelBag.class).then(l -> {
            l.forEach(action);
        });

        //use default iterator
        //iterator().forEachRemaining(action);
    }

}