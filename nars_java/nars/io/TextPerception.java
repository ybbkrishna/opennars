package nars.io;

import java.util.ArrayList;
import java.util.List;
import nars.core.NAR;
import nars.core.Parameters;
import nars.entity.BudgetValue;
import nars.entity.Sentence;
import nars.entity.Stamp;
import nars.entity.Task;
import nars.entity.TruthValue;
import nars.inference.BudgetFunctions;
import static nars.io.Symbols.ARGUMENT_SEPARATOR;
import static nars.io.Symbols.BUDGET_VALUE_MARK;
import static nars.io.Symbols.GOAL_MARK;
import static nars.io.Symbols.INPUT_LINE_PREFIX;
import static nars.io.Symbols.JUDGMENT_MARK;
import nars.io.Symbols.NativeOperator;
import static nars.io.Symbols.NativeOperator.COMPOUND_TERM_CLOSER;
import static nars.io.Symbols.NativeOperator.COMPOUND_TERM_OPENER;
import static nars.io.Symbols.NativeOperator.SET_EXT_CLOSER;
import static nars.io.Symbols.NativeOperator.SET_INT_CLOSER;
import static nars.io.Symbols.NativeOperator.SET_INT_OPENER;
import static nars.io.Symbols.NativeOperator.STATEMENT_CLOSER;
import static nars.io.Symbols.NativeOperator.STATEMENT_OPENER;
import static nars.io.Symbols.OUTPUT_LINE_PREFIX;
import static nars.io.Symbols.PREFIX_MARK;
import static nars.io.Symbols.QUESTION_MARK;
import static nars.io.Symbols.QUEST_MARK;
import static nars.io.Symbols.STAMP_CLOSER;
import static nars.io.Symbols.STAMP_OPENER;
import static nars.io.Symbols.TRUTH_VALUE_MARK;
import static nars.io.Symbols.VALUE_SEPARATOR;
import nars.language.Interval;
import nars.language.SetExt;
import nars.language.SetInt;
import nars.language.Statement;
import nars.language.Term;
import nars.language.Terms;
import nars.language.Variable;
import nars.language.Variables;
import nars.operator.Operation;
import nars.operator.Operator;
import nars.storage.Memory;

/**
 * Processes text input
 */
public class TextPerception {
    
    public final Memory memory;
    
    public final List<TextReaction> parsers;
    private final NAR nar;
    
    
    /**
     * All kinds of invalid addInput lines
     */
    public static class InvalidInputException extends Exception {

        /**
         * An invalid addInput line.
         *
         * @param s type of error
         */
        InvalidInputException(String s) {
            super(s);
        }
    }    
    
    public TextPerception(NAR nar) {
        this(nar, new ArrayList());
    }

    public TextPerception(NAR nar, List<TextReaction> parsers) {
        this.nar = nar;
        this.memory = nar.memory;
        this.parsers = parsers;        
    }
    
    public void perceive(final Input i, final String lines) {
        perceive(i, lines.split("\n"));        
    }
        
    protected void perceive(final Input i, final String[] lines) {
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            TextReaction lastHandled = null;
            for (TextReaction p : parsers) {            

                boolean result = p.react(memory, line, lastHandled);

                //System.out.println(line + " parser " + p + " " + result);

                if (result)
                    lastHandled = p;
            }        

            //not handled, so respond with some signal
            if (lastHandled == null) {
                memory.output(Output.ERR.class, "Invalid input (" + i + "): " + line);
            }
        }
        
    }
        
    
    

    /**
     * Parse a line of addInput experience
     * <p>
     * called from ExperienceIO.loadLine
     *
     * @param buffer The line to be parsed
     * @param memory Reference to the memory
     * @param time The current time
     * @return An experienced task
     */
    public static Task parseNarsese(StringBuilder buffer, Memory memory, long time) throws InvalidInputException {
        int i = buffer.indexOf(String.valueOf(PREFIX_MARK));
        if (i > 0) {
            String prefix = buffer.substring(0, i).trim();
            if (prefix.equals(INPUT_LINE_PREFIX)) {
                buffer.delete(0, i + 1);                
            }
            else if (prefix.equals(OUTPUT_LINE_PREFIX)) {
                //ignore outputs
                return null;                
            }            
        }
        char c = buffer.charAt(buffer.length() - 1);
        if (c == STAMP_CLOSER) {
            //ignore stamp
            int j = buffer.lastIndexOf(String.valueOf(STAMP_OPENER));
            buffer.delete(j - 1, buffer.length());
        }
        return parseTask(buffer.toString().trim(), memory, time);
    }


    public static Sentence parseOutput(String s) {
        Term content = null;
        char punc = 0;
        TruthValue truth = null;
        
        try {
            StringBuilder buffer = new StringBuilder(s);
            //String budgetString = getBudgetString(buffer);
            String truthString = getTruthString(buffer);
            String str = buffer.toString().trim();
            int last = str.length() - 1;
            punc = str.charAt(last);
            //Stamp stamp = new Stamp(time);
            truth = parseTruth(truthString, punc);


            /*Term content = parseTerm(str.substring(0, last), memory);
            if (content == null) throw new InvalidInputException("Content term missing");*/
        }
        catch (InvalidInputException e) {
            System.err.println("TextInput.parseOutput: " + s + " : " + e.toString());
        }
        return new Sentence(content, punc, truth, null);        
    }
    
    /**
     * Enter a new Task in String into the memory, called from InputWindow or
     * locally.
     *
     * @param s the single-line addInput String
     * @param memory Reference to the memory
     * @param time The current time
     * @return An experienced task
     */    
    public static Task parseTask(String s, Memory memory, long time) throws InvalidInputException {
        StringBuilder buffer = new StringBuilder(s);
        try {
            
            String budgetString = getBudgetString(buffer);
            String truthString = getTruthString(buffer);
            String tense = parseTense(buffer);
            String str = buffer.toString().trim();
            int last = str.length() - 1;
            char punc = str.charAt(last);
            Stamp stamp = new Stamp(time, tense, memory.newStampSerial());
            TruthValue truth = parseTruth(truthString, punc);
            Term content = parseTerm(str.substring(0, last), memory);
            if (content == null) throw new InvalidInputException("Content term missing");
            Sentence sentence = new Sentence(content, punc, truth, stamp);
            //if ((content instanceof Conjunction) && Variable.containVarDep(content.getName())) {
            //    sentence.setRevisible(false);
            //}
            BudgetValue budget = parseBudget(budgetString, punc, truth);
            Task task = new Task(sentence, budget);
            return task;
        }
        catch (InvalidInputException e) {
            throw new InvalidInputException(" !!! INVALID INPUT: parseTask: " + buffer + " --- " + e.getMessage());         
        }

    }

    /* ---------- react values ---------- */
    /**
     * Return the prefix of a task string that contains a BudgetValue
     *
     * @param s the addInput in a StringBuilder
     * @return a String containing a BudgetValue
     * @throws nars.io.StringParser.InvalidInputException if the addInput cannot be
 parsed into a BudgetValue
     */
    private static String getBudgetString(StringBuilder s) throws InvalidInputException {
        if (s.charAt(0) != BUDGET_VALUE_MARK) {
            return null;
        }
        int i = s.indexOf(String.valueOf(BUDGET_VALUE_MARK), 1);    // looking for the end
        if (i < 0) {
            throw new InvalidInputException("missing budget closer");
        }
        String budgetString = s.substring(1, i).trim();
        if (budgetString.length() == 0) {
            throw new InvalidInputException("empty budget");
        }
        s.delete(0, i + 1);
        return budgetString;
    }

    /**
     * Return the postfix of a task string that contains a TruthValue
     *
     * @return a String containing a TruthValue
     * @param s the addInput in a StringBuilder
     * @throws nars.io.StringParser.InvalidInputException if the addInput cannot be
 parsed into a TruthValue
     */
    private static String getTruthString(final StringBuilder s) throws InvalidInputException {
        final int last = s.length() - 1;
        if (s.charAt(last) != TRUTH_VALUE_MARK) {       // use default
            return null;
        }
        final int first = s.indexOf(String.valueOf(TRUTH_VALUE_MARK));    // looking for the beginning
        if (first == last) { // no matching closer
            throw new InvalidInputException("missing truth mark");
        }
        final String truthString = s.substring(first + 1, last).trim();
        if (truthString.length() == 0) {                // empty usage
            throw new InvalidInputException("empty truth");
        }
        s.delete(first, last + 1);                 // remaining addInput to be processed outside
        s.trimToSize();
        return truthString;
    }

    /**
     * react the addInput String into a TruthValue (or DesireValue)
     *
     * @param s addInput String
     * @param type Task type
     * @return the addInput TruthValue
     */
    private static TruthValue parseTruth(String s, char type) {
        if ((type == QUESTION_MARK) || (type == QUEST_MARK)) {
            return null;
        }
        float frequency = 1.0f;
        float confidence = Parameters.DEFAULT_JUDGMENT_CONFIDENCE;
        if (s != null) {
            int i = s.indexOf(VALUE_SEPARATOR);
            if (i < 0) {
                frequency = Float.parseFloat(s);
            } else {
                frequency = Float.parseFloat(s.substring(0, i));
                confidence = Float.parseFloat(s.substring(i + 1));
            }
        }
        return new TruthValue(frequency, confidence);
    }

    /**
     * react the addInput String into a BudgetValue
     *
     * @param truth the TruthValue of the task
     * @param s addInput String
     * @param punctuation Task punctuation
     * @return the addInput BudgetValue
     * @throws nars.io.StringParser.InvalidInputException If the String cannot
     * be parsed into a BudgetValue
     */
    private static BudgetValue parseBudget(String s, char punctuation, TruthValue truth) throws InvalidInputException {
        float priority, durability;
        switch (punctuation) {
            case JUDGMENT_MARK:
                priority = Parameters.DEFAULT_JUDGMENT_PRIORITY;
                durability = Parameters.DEFAULT_JUDGMENT_DURABILITY;
                break;
            case QUESTION_MARK:
                priority = Parameters.DEFAULT_QUESTION_PRIORITY;
                durability = Parameters.DEFAULT_QUESTION_DURABILITY;
                break;
            case GOAL_MARK:
                priority = Parameters.DEFAULT_GOAL_PRIORITY;
                durability = Parameters.DEFAULT_GOAL_DURABILITY;
                break;
            case QUEST_MARK:
                priority = Parameters.DEFAULT_QUEST_PRIORITY;
                durability = Parameters.DEFAULT_QUEST_DURABILITY;
                break;                
            default:
                throw new InvalidInputException("unknown punctuation: '" + punctuation + "'");
        }
        if (s != null) { // overrite default
            int i = s.indexOf(VALUE_SEPARATOR);
            if (i < 0) {        // default durability
                priority = Float.parseFloat(s);
            } else {
                priority = Float.parseFloat(s.substring(0, i));
                durability = Float.parseFloat(s.substring(i + 1));
            }
        }
        float quality = (truth == null) ? 1 : BudgetFunctions.truthToQuality(truth);
        return new BudgetValue(priority, durability, quality);
    }

    /**
     * Recognize the tense of an addInput sentence
     * @param s the addInput in a StringBuilder
     * @return a tense value
     */
    private static String parseTense(StringBuilder s) {
        int i = s.indexOf(Symbols.TENSE_MARK);
        String t = "";
        if (i > 0) {
            t = s.substring(i).trim();
            s.delete(i, s.length());
        }
        return t;
    }

    /* ---------- react String into term ---------- */
    /**
     * Top-level method that react a Term in general, which may recursively call
 itself.
     * <p>
 There are 5 valid cases: 1. (Op, A1, ..., An) is a CompoundTerm if Op is
 a built-in getOperator 2. {A1, ..., An} is an SetExt; 3. [A1, ..., An] is an
 SetInt; 4. <T1 Re T2> is a Statement (including higher-order Statement);
     * 5. otherwise it is a simple term.
     *
     * @param s0 the String to be parsed
     * @param memory Reference to the memory
     * @return the Term generated from the String
     */
    public static Term parseTerm(String s0, Memory memory) throws InvalidInputException {
        String s = s0.trim();
        
        try {
            
            if (s.length() == 0) {
                throw new InvalidInputException("missing content");
            }
            
            Term t = memory.nameToTerm(s);    // existing constant or getOperator
            if (t != null) {
                return t;
            }                           

            // existing Term                
            int index = s.length() - 1;
            char first = s.charAt(0);
            char last = s.charAt(index);
            
            NativeOperator opener = Symbols.getOpener(first);
            if (opener!=null) {
                switch (opener) {
                    case COMPOUND_TERM_OPENER:
                        if (last == COMPOUND_TERM_CLOSER.ch) {
                           return parseCompoundTerm(s.substring(1, index), memory);
                        } else {
                            throw new InvalidInputException("missing CompoundTerm closer");
                        }
                    case SET_EXT_OPENER:
                        if (last == SET_EXT_CLOSER.ch) {
                            return SetExt.make(parseArguments(s.substring(1, index) + ARGUMENT_SEPARATOR, memory), memory);
                        } else {
                            throw new InvalidInputException("missing ExtensionSet closer");
                        }                    
                    case SET_INT_OPENER:
                        if (last == SET_INT_CLOSER.ch) {
                            return SetInt.make(parseArguments(s.substring(1, index) + ARGUMENT_SEPARATOR, memory), memory);
                        } else {
                            throw new InvalidInputException("missing IntensionSet closer");
                        }   
                    case STATEMENT_OPENER:
                        if (last == STATEMENT_CLOSER.ch) {
                            return parseStatement(s.substring(1, index), memory);
                        } else {
                            throw new InvalidInputException("missing Statement closer");
                        }
                }
            }
            
            //if no opener, parse the term            
            return parseAtomicTerm(s);

        } catch (InvalidInputException e) {
            throw new InvalidInputException(" !!! INVALID INPUT: parseTerm: " + s + " --- " + e.getMessage());
        }

    }

//    private static void showWarning(String message) {
//		new TemporaryFrame( message + "\n( the faulty line has been kept in the addInput window )",
//				40000, TemporaryFrame.WARNING );
//    }
    /**
     * Parse a Term that has no internal structure.
     * <p>
     * The Term can be a constant or a variable.
     *
     * @param s0 the String to be parsed
     * @throws nars.io.StringParser.InvalidInputException the String cannot be
     * parsed into a Term
     * @return the Term generated from the String
     */
    private static Term parseAtomicTerm(String s0) throws InvalidInputException {
        String s = s0.trim();
        if (s.length() == 0) {
            throw new InvalidInputException("missing term");
        }
        if (s.contains(" ")) // invalid characters in a name
        {
            throw new InvalidInputException("invalid term");
        }
        
        char c = s.charAt(0);
        if (c == Symbols.INTERVAL_PREFIX) {
            return new Interval(s);
        }
 
        if (Variables.containVar(s)) {
            return new Variable(s);
        } else {
            return new Term(s);
        }
    }

    /**
     * Parse a String to create a Statement.
     *
     * @return the Statement generated from the String
     * @param s0 The addInput String to be parsed
     * @throws nars.io.StringParser.InvalidInputException the String cannot be
     * parsed into a Term
     */
    private static Statement parseStatement(String s0, Memory memory) throws InvalidInputException {
        String s = s0.trim();
        int i = topRelation(s);
        if (i < 0) {
            throw new InvalidInputException("invalid statement: topRelation(s) < 0");
        }
        String relation = s.substring(i, i + 3);
        Term subject = parseTerm(s.substring(0, i), memory);
        Term predicate = parseTerm(s.substring(i + 3), memory);
        Statement t = Statement.make(Symbols.getRelation(relation), subject, predicate, memory);
        if (t == null) {
            throw new InvalidInputException("invalid statement: statement unable to create: " + Symbols.getOperator(relation) + " " + subject + " " + predicate);
        }
        return t;
    }

    /**
     * Parse a String to create a CompoundTerm.
     *
     * @return the Term generated from the String
     * @param s0 The String to be parsed
     * @throws nars.io.StringParser.InvalidInputException the String cannot be
     * parsed into a Term
     */
    private static Term parseCompoundTerm(final String s0, final Memory memory) throws InvalidInputException {
        String s = s0.trim();
        if (s.isEmpty()) {
            throw new InvalidInputException("Empty compound term: " + s);
        }
        int firstSeparator = s.indexOf(ARGUMENT_SEPARATOR);
        if (firstSeparator == -1) {
            throw new InvalidInputException("Invalid compound term (missing ARGUMENT_SEPARATOR): " + s);
        }
        
        String op = s.substring(0, firstSeparator).trim();
        NativeOperator oNative = Symbols.getOperator(op);
        Operator oRegistered = memory.getOperator(op);
        
        if ((oRegistered==null) && (oNative == null)) {
            throw new InvalidInputException("Unknown operator: " + op);
        }

        ArrayList<Term> arg = parseArguments(s.substring(firstSeparator + 1) + ARGUMENT_SEPARATOR, memory);
        Term[] argA = arg.toArray(new Term[arg.size()]);
        
        Term t;
        
        if (oNative!=null) {
            t = Terms.make(oNative, argA, memory);
        }
        else if (oRegistered!=null) {
            t = Operation.make(oRegistered, argA, memory);
        }
        else {
            throw new InvalidInputException("Invalid compound term");
        }
        
        return t;
    }

    /**
     * Parse a String into the argument get of a CompoundTerm.
     *
     * @return the arguments in an ArrayList
     * @param s0 The String to be parsed
     * @throws nars.io.StringParser.InvalidInputException the String cannot be
     * parsed into an argument get
     */
    private static ArrayList<Term> parseArguments(String s0, Memory memory) throws InvalidInputException {
        String s = s0.trim();
        ArrayList<Term> list = new ArrayList<>();
        int start = 0;
        int end = 0;
        Term t;
        while (end < s.length() - 1) {
            end = nextSeparator(s, start);
            t = parseTerm(s.substring(start, end), memory);     // recursive call
            list.add(t);
            start = end + 1;
        }
        if (list.isEmpty()) {
            throw new InvalidInputException("null argument");
        }
        return list;
    }

    /* ---------- locate top-level substring ---------- */
    /**
     * Locate the first top-level separator in a CompoundTerm
     *
     * @return the index of the next seperator in a String
     * @param s The String to be parsed
     * @param first The starting index
     */
    private static int nextSeparator(String s, int first) {
        int levelCounter = 0;
        int i = first;
        while (i < s.length() - 1) {
            if (isOpener(s, i)) {
                levelCounter++;
            } else if (isCloser(s, i)) {
                levelCounter--;
            } else if (s.charAt(i) == ARGUMENT_SEPARATOR) {
                if (levelCounter == 0) {
                    break;
                }
            }
            i++;
        }
        return i;
    }

    /**
     * locate the top-level getRelation in a statement
     *
     * @return the index of the top-level getRelation
     * @param s The String to be parsed
     */
    private static int topRelation(final String s) {      // need efficiency improvement
        int levelCounter = 0;
        int i = 0;
        while (i < s.length() - 3) {    // don't need to check the last 3 characters
            if ((levelCounter == 0) && (Symbols.isRelation(s.substring(i, i + 3)))) {
                return i;
            }
            if (isOpener(s, i)) {
                levelCounter++;
            } else if (isCloser(s, i)) {
                levelCounter--;
            }
            i++;
        }
        return -1;
    }

    /* ---------- recognize symbols ---------- */
    /**
     * Check CompoundTerm opener symbol
     *
     * @return if the given String is an opener symbol
     * @param s The String to be checked
     * @param i The starting index
     */
    private static boolean isOpener(final String s, final int i) {
        char c = s.charAt(i);
        
        boolean b = (Symbols.getOpener(c)!=null);
        if (!b)
            return false;
        
        return i + 3 > s.length() || !Symbols.isRelation(s.substring(i, i + 3));
    }

    /**
     * Check CompoundTerm closer symbol
     *
     * @return if the given String is a closer symbol
     * @param s The String to be checked
     * @param i The starting index
     */
    private static boolean isCloser(String s, int i) {
        char c = s.charAt(i);

        boolean b = (Symbols.getCloser(c)!=null);
        if (!b)
            return false;
        
        return i < 2 || !Symbols.isRelation(s.substring(i - 2, i + 1));
    }
    
}
