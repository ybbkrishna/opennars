package nars;

import nars.core.Events;
import nars.core.Events.Answer;
import nars.core.Events.ConceptBeliefAdd;
import nars.core.Events.ConceptBeliefRemove;
import nars.core.Memory;
import nars.core.NAR;
import nars.core.Parameters;
import nars.core.Events.ERR;
import nars.core.Events.IN;
import nars.core.Events.OUT;
import nars.io.Texts;
import nars.logic.entity.*;
import nars.logic.entity.Term;
import nars.logic.nal1.Inheritance;
import nars.logic.nal1.Negation;
import nars.logic.nal2.Similarity;
import nars.logic.nal3.SetExt;
import nars.logic.nal3.SetInt;
import nars.logic.nal3.SetTensional;
import nars.logic.nal4.Product;
import nars.logic.nal5.Equivalence;
import nars.logic.nal5.Implication;
import nars.logic.nal7.TemporalRules;
import nars.logic.nal7.Tense;
import nars.prolog.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Iterators.toArray;

/**
 * Causes a NARProlog to mirror certain activity of a NAR.  It generates
 * prolog terms from NARS beliefs, and answers NARS questions with the results
 * of a prolog solution (converted to NARS terms), which are input to NARS memory
 * with the hope that this is sooner than NARS can solve it by itself. 
 */
public class NARPrologMirror extends AbstractMirror {

    public final NAR nar;
    public final NARTuprolog prolog;

    Theory axioms;

    private float trueThreshold = 0.80f;
    private float falseThreshold = 0.20f;
    private float confidenceThreshold;
    private final Map<Sentence,nars.prolog.Term> beliefs = new HashMap();
        
    private boolean eternalJudgments = true;
    private boolean presentJudgments = false;

    /** how much to scale the memory's duration parameter for this reasoner's "now" duration; default=1.0 */
    float durationMultiplier = 1.0f;
    
    /** how often to remove temporally irrelevant beliefs */
    @Deprecated float forgetCyclePeriod; ///TODO use a Map<Long,belief> indexed by expiration time, so they can be removed efficiently
    private long lastFlush;
    private int durationCycles;
    
    boolean allTerms = false;    
    
    /** in seconds */
    float maxSolveTime;
    float minSolveTime;

    /** max # answers returned in response to a question */
    int maxAnswers = 3;
    
    boolean reportAssumptions = false;
    boolean reportForgets = false;
    boolean reportAnswers = false;
    

    
    public static final Class[] telepathicEvents = { Events.ConceptBeliefAdd.class, Events.ConceptBeliefRemove.class, Events.ConceptQuestionAdd.class, IN.class, OUT.class, Answer.class };
    
    public static final Class[] inputOutputEvents = { IN.class, OUT.class };
    private InputMode inputMode = InputMode.InputTask;

    //serial #'s
    long nextQueryID = 0;
    long variableContext = 0;

    public NARPrologMirror(NAR nar, float minConfidence, boolean telepathic, boolean eternalJudgments, boolean presentJudgments) {
        super(nar, true, telepathic ? telepathicEvents : inputOutputEvents );
        this.nar = nar;
        this.confidenceThreshold = minConfidence;
        this.prolog = new NARTuprolog(nar);
        this.forgetCyclePeriod = nar.memory.getDuration() / 2;
        this.maxSolveTime = 40.0f / 1e3f;
        this.minSolveTime = maxSolveTime/2f;

        try {
            nars.prolog.Term[] ax = toArray(new Theory(getAxiomString()).iterator(prolog.prolog), nars.prolog.Term.class);
            axioms = new Theory(ax);
        } catch (InvalidTheoryException e) {
            e.printStackTrace();
            System.exit(1);
        }


        setTemporalMode(eternalJudgments, presentJudgments);
    }

    public NARPrologMirror setInputMode(InputMode i) {
        this.inputMode = i;
        return this;
    }
    public NARPrologMirror setTemporalMode(boolean eternalJudgments, boolean presentJudgments) {
        this.eternalJudgments = eternalJudgments;
        this.presentJudgments = presentJudgments;
        return this;
    }
    
    boolean validTemporal(Sentence s) {        
        long e = s.getOccurenceTime();
        
        if (eternalJudgments && (e == Stamp.ETERNAL))
            return true;
        
        if (presentJudgments) {
            long now = nar.time();            
            if (TemporalRules.concurrent(now, e, (int)(durationCycles * durationMultiplier)))
               return true;
        }
        
        return false;
    }

    public Map<Sentence, nars.prolog.Term> getBeliefs() {
        return beliefs;
    }

    protected void beliefsChanged() {
    }
    
    protected boolean forget(Sentence belief) {
        if (beliefs.remove(belief)!=null) {
            
            beliefsChanged();

            if (reportForgets) {
                System.err.println("Prolog forget: " + belief);                
            }
            return true;
        }
        return false;
    }
    
    protected void updateBeliefs() {
        if (presentJudgments) {
            long now = nar.time();
            durationCycles = (nar.param).duration.get();
            if (now - lastFlush > (long)(durationCycles/ forgetCyclePeriod) ) {
                
                Set<Sentence> toRemove = new HashSet();
                for (Sentence s : beliefs.keySet()) {
                    if (!validTemporal(s)) {
                        toRemove.add(s);
                    }
                }
                for (Sentence s : toRemove) {                    
                    forget(s);
                }
                
                lastFlush = now;
            }                
        }
    }
    
    @Override
    public void event(final Class channel, final Object... arg) {        
        
        if (channel == ConceptBeliefAdd.class) {
            Concept c = (Concept)arg[0];            
            Task task = (Task)arg[1];
            add(task.sentence, task);            
        }
        else if (channel == ConceptBeliefRemove.class) {
            Concept c = (Concept)arg[0];
            remove(c, (Sentence)arg[1]);
        }        
        else if (channel == Events.ConceptQuestionAdd.class) {
            Concept c = (Concept)arg[0];            
            Task task = (Task)arg[1];
            add(task.sentence, task);
        }        
        else if ((channel == IN.class) || (channel == OUT.class)) {
            Object o = arg[0];
            if (o instanceof Task) {
                Task task = (Task)o;
                Sentence s = task.sentence;
                
                add(s, task);
            }
        }
    }

    /** remove belief unless there are other similar beliefs remaining in 'c' */
    private void remove(Concept c, Sentence forgotten) {
        for (Sentence x : c.getBeliefs()) {
            if (x.equals(forgotten)) continue;
            if (believable(x.truth) && similarTruth(x.truth, forgotten.truth) && similarTense(x, forgotten)) {
                //there still remains evidence for this belief in the concept
                return;
            }
        }

        remove(forgotten, null);
    }

    protected void remove(Sentence s, Task task) {
        //TODO
    }
    
    protected void add(Sentence s, Task task) {

        variableContext = s.term.hashCode();

        if (!(s.term instanceof CompoundTerm))
            return;        

        if (!validTemporal(s))
            return;

        updateBeliefs();
        
        //only interpret input judgments, or any kind of question
        if (s.isJudgment()) {

            processBelief(s, task, true);
        }
        else if (s.isQuestion()) {

            //System.err.println("question: " + s);
            onQuestion(s);
            
            float priority = task.getPriority();
            float solveTime = ((maxSolveTime - minSolveTime) * priority) + minSolveTime;

            if (beliefs.containsKey(s)) {
                //TODO search for opposite belief

                //already determined it to be true
                answer(task, s.term, null);
                return;
            }
            
            try {
                Struct qh = newQuestion(s);
                
                if (qh!=null) {
                    //System.out.println("Prolog question: " + s.toString() + " | " + qh.toString() + " ? (" + Texts.n2(priority) + ")");    

                    Theory t = getTheory(beliefs);
                    t.append(axioms);

                    prolog.setTheory(t);

                    SolveInfo si = prolog.query(qh, solveTime);

                    int answers = 0;

                    nars.prolog.Term lastSolution = null;
                    
                    do {
                        if (si == null) break;

                        nars.prolog.Term solution = si.getSolution();
                        if (solution == null)
                            break;

                        if (lastSolution!=null && solution.equals(lastSolution))
                            continue;

                        lastSolution = solution;

                        try {
                            Term n = nterm(solution);
                            if (n!=null)
                                answer(task, n, solution);
                            else
                                onUnanswerable(solution);
                        }
                        catch (Exception e) {
                            //problem generating a result
                            e.printStackTrace();
                        }

                        si = prolog.prolog.solveNext(solveTime);

                        solveTime /= 2d;
                    }
                    while ((answers++) < maxAnswers);

                    prolog.prolog.solveEnd();

                }
                else {
                    onUnrecognizable(s);
                }
            } catch (NoSolutionException nse) {
                //no solution, ok
            } catch (InvalidTermException nse) {
                nar.emit(NARPrologMirror.class, s + " : not supported yet");       
                nse.printStackTrace();
            } catch (Exception ex) {                        
                nar.emit(ERR.class, ex.toString());
                ex.printStackTrace();
            }

        }
        
    }

    protected void onUnrecognizable(Sentence s) {
        //System.err.println(this + " unable to express question in Prolog: " + s);
    }

    protected void onUnanswerable(nars.prolog.Term solution) {
        //System.err.println(this + " unable to answer solution: " + solution);

    }

    protected void processBelief(Sentence s, Task task, boolean addOrRemove) {
            
        TruthValue tv = s.truth;
        if (believable(tv)) {

            boolean exists = beliefs.containsKey(s.term);
            if ((addOrRemove) && (exists))
                return;
            else if ((!addOrRemove) && (!exists))
                return;

            try {
                Struct th = newJudgmentTheory(s);
                if (th!=null) {

                    if (tv.getFrequency() < falseThreshold) {
                        th = negation(th);
                    }

                    if (addOrRemove) {
                        if (beliefs.putIfAbsent(s, th)==null) {

                            beliefsChanged();

                            if (reportAssumptions)
                                System.err.println("Prolog assume: " + th + " | " + s);
                        }
                    }
                    else {
                        forget(s);
                    }

                }
            } catch (Exception ex) {
                nar.emit(ERR.class, ex.toString());
            }
        }


    }
    
    /** creates a theory from a judgment Statement */
    Struct newJudgmentTheory(final Sentence judgment) throws InvalidTheoryException {
        
        nars.prolog.Term s;
        /*if (judgment.truth!=null) {            
            s = pInfer(pterm(judgment.content), judgment.truth);
        }
        else {*/
        try {
            s = pterm(judgment.term);
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        //}
        
        return (Struct) s;            
    }
    
    Struct newQuestion(final Sentence question) {
        nars.prolog.Term s = pterm(question.term);
        return (Struct) s;
    }

    //NOT yet working
    public Struct pInfer(nars.prolog.Term t, TruthValue tv) {
        double freq = tv.getFrequency();
        double conf = tv.getConfidence();
        Struct lt = new Struct(new nars.prolog.Term[] { t, 
            new Struct( new nars.prolog.Term[] { 
                new nars.prolog.Double(freq), 
                new nars.prolog.Double(conf) 
            }) 
        });        
        return new Struct("infer", lt);
    }
    
    public Struct negation(nars.prolog.Term t) {
        return new Struct("negation", t);
    }

    public String pescape(String p) {
        return Texts.escapeLiteral(p).toString();
    }
    public String unpescape(String p) {
        return Texts.unescapeLiteral(p).toString();
    }

    public boolean believable(TruthValue tv) {
        return (tv.getConfidence() > confidenceThreshold) && ((tv.getFrequency() > trueThreshold) || (tv.getFrequency() < falseThreshold));
    }

    public boolean similarTense(Sentence a, Sentence b) {
        boolean ae = a.isEternal();
        boolean be = b.isEternal();
        if (ae && be) return true;
        else if (ae && !be) return false;
        else if (!ae && be) return false;
        else {
            return (TemporalRules.concurrent(a.getOccurenceTime(), b.getOccurenceTime(), nar.memory.getDuration()));
        }
    }


    public boolean similarTruth(TruthValue a, TruthValue b) {
        float af = a.getFrequency();
        float bf = b.getFrequency();
        if ((af < falseThreshold) && (bf < falseThreshold))
            return true;
        if ((af > trueThreshold) && (bf > trueThreshold))
            return true;
        return false;
    }

    protected static String classPredicate(Class c) {
        return c.getSimpleName().toLowerCase();
    }
    
    //NARS term -> Prolog term
    public nars.prolog.Term pterm(final Term term) {
        
        //CharSequence s = termString(term);
        if (term instanceof Statement) {
            Statement i = (Statement)term;
            String predicate = classPredicate(i.getClass());
            nars.prolog.Term subj = pterm(i.getSubject());
            nars.prolog.Term obj = pterm(i.getPredicate());
            if ((subj!=null) && (obj!=null))
                return new Struct(predicate, subj, obj);
        }
        else if ((term instanceof SetTensional) || (term instanceof Product) /* conjunction */) {
            CompoundTerm s = (CompoundTerm)term;
            String predicate = classPredicate(s.getClass());
            nars.prolog.Term[] args = pterms(s.term);
            if (args!=null)
                return new Struct(predicate, args);
        }
        //Image...
        //Conjunction...
        else if (term instanceof Negation) {
            nars.prolog.Term np = pterm(((Negation)term).term[0]);
            if (np == null) return null;
            return new Struct("negation", np);
        }
        else if (term.getClass().equals(Variable.class)) {
            return getVariable((Variable)term);
        }
        else if (term.getClass().equals(Term.class)) {
            return new Struct(pescape(term.name().toString()));
        }
        else if (term instanceof CompoundTerm) {
            //unhandled type of compound term, store as an atomic string            
            //NOT ready yet
            if (allTerms) {
                return new Struct("_" + pescape(term.name().toString()));
            }
        }
        
        return null;        
    }
    private Term getVar(Var v) {
        //assume it is a dependent variable
        return new Variable("#" + v.getName());
    }


    private Var getVariable(Variable v) {
        if (v.hasVarIndep())
            return new Var("I" + pescape(v.name().toString()));
        if (v.hasVarQuery())
            return new Var("Q" + nextQueryID++);
        if (v.hasVarDep()) //check this
            return new Var("D" + (variableContext) + "_" + pescape(v.name().toString()));
        return null;
    }

    /** Prolog term --> NARS statement */
    public Term nterm(final nars.prolog.Term term) {
        Memory mem = nar.memory;
        
        if (term instanceof Struct) {
            Struct s = (Struct)term;
            int arity = s.getArity();
            String predicate = s.name().toString();
            if (arity == 0) {
                return Term.get(unpescape(predicate));
            }
            if (arity == 1) {
                switch (predicate) {
                    case "negation":
                        return Negation.make(nterm(s.getArg(0)));
                }
            }
            if (predicate.equals("product")) {
                Term[] a = nterm(s.getArg());
                if (a!=null) return new Product(a);
                else return null;
            }
            if (predicate.equals("setint")) {
                Term[] a = nterm(s.getArg());
                if (a!=null) return new SetInt(nterm(s.getArg()));
                else return null;
            }
            if (predicate.equals("setext")) {
                Term[] a = nterm(s.getArg());
                if (a!=null) return new SetExt(nterm(s.getArg()));
                else return null;
            }
            if (arity == 2) {                
                Term a = nterm(s.getArg(0));
                Term b = nterm(s.getArg(1));
                if ((a!=null) && (b!=null)) {
                    switch (predicate) {
                        case "inheritance":
                            return Inheritance.make(a, b);
                        case "similarity":
                            return Similarity.make(a, b);
                        case "implication":
                            return Implication.make(a, b);
                        case "equivalence":
                            return Equivalence.make(a, b);
                        //TODO more types
                            
                    }
                }
            }
            System.err.println("nterm() does not yet support translation to NARS terms of Prolog: " + term);
        }
        else if (term instanceof Var) {
            Var v = (Var)term;
            nars.prolog.Term t = v.getTerm();
            if (t!=v) {
                //System.out.println("Bound: " + v + " + -> " + t + " " + nterm(t));
                return nterm(t);
            }
            else {
                //System.out.println("Unbound: " + v);
                //unbound variable, is there anything we can do with it?
                return getVar(v);
            }
        }
        else if (term instanceof nars.prolog.Number) {
            nars.prolog.Number n = (nars.prolog.Number)term;
            return new Term('"' + String.valueOf(n.doubleValue()) + '"');
        }
        
        return null;
    }


    public Sentence getBeliefSentence(Sentence question, Term belief, Task parentTask) {
        float freq = 1.0f;
        float conf = Parameters.DEFAULT_JUDGMENT_CONFIDENCE;
        float priority = Parameters.DEFAULT_JUDGMENT_PRIORITY;
        float durability = Parameters.DEFAULT_JUDGMENT_DURABILITY;
        Tense tense = question.isEternal() ? Tense.Eternal : Tense.Present;

        //TODO use derivation of prolog result to create a correct stamp

        return new Sentence(belief, '.', new TruthValue(freq, conf),
                new Stamp(nar.memory, tense));
    }
    
    /** reflect a result to NARS, and remember it so that it doesn't get reprocessed here later */
    public Term answer(Task question, Term t, nars.prolog.Term pt) {
        if (reportAnswers)
            System.err.println("Prolog answer: " + t);
        
        Sentence a = getBeliefSentence(question.sentence, t, question);
        
        input(a, inputMode, question);
        
        if (pt!=null) {
            beliefs.put(a, pt);
            beliefsChanged();
        }
        
        return t;
    }

    /*
    public static class NARStruct extends Struct {
        
        Sentence sentence = null;

        public NARStruct(Sentence sentence, String predicate, nars.prolog.Term[] args) {
            super(predicate, args);
            
            this.sentence = sentence;
        }
        
        public NARStruct(String predicate, nars.prolog.Term... args) {
            this(null, predicate, args);
        }

        public Sentence getSentence() {
            return sentence;
        }

        public void setSentence(Sentence sentence) {
            this.sentence = sentence;
        }
        
        
    }
    */
    




    private String getAxiomString() {
        return
                    "inheritance(A, C) :- inheritance(A,B),inheritance(B,C). " + '\n' +
                    "similarity(A, B) :- inheritance(A,B),inheritance(B,A). " + '\n' +

                    "implication(A, C) :- implication(A,B),implication(B,C). " + '\n' +

                    "similarity(A, B) :- similarity(B,A). " + '\n' +
                    "not(similar(A, B)) :- not(inheritance(A,B)),inheritance(B,A). " + '\n' +

                    "equivalence(A, B) :- equivalence(B,A). " + '\n' +
                    "similarity(A, B) :- equivalence(A,B). " + '\n' +

                    "not(equivalence(A, B)) :- not(similar(A,B)). " + '\n' +


                    "A :- not(not(A))." + '\n';
    }
    
    public static Theory getTheory(Map<Sentence, nars.prolog.Term> beliefMap) throws InvalidTheoryException  {
        return new Theory(new Struct(beliefMap.values().toArray(new Struct[beliefMap.size()])));
    }
    
    public Theory getBeliefsTheory() throws InvalidTheoryException {
        return getTheory(beliefs);
    }

    protected void onQuestion(Sentence s) {
    }

    protected nars.prolog.Term[] pterms(Term[] term) {
        nars.prolog.Term[] tt = new nars.prolog.Term[term.length];
        int i = 0;
        for (Term x : term) {
            if ((tt[i++] = pterm(x)) == null) return null;
        }
        return tt;
    }

    public Term[] nterm(final nars.prolog.Term[] term) {
        Term[] tt = new Term[term.length];
        int i = 0;
        for (nars.prolog.Term x : term) {
            if ((tt[i++] = nterm(x)) == null) return null;
        }
        return tt;        
    }

    
}

