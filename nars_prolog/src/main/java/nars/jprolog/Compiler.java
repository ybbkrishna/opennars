package nars.jprolog;
import nars.jprolog.lang.Predicate;
import nars.jprolog.lang.SymbolTerm;
import nars.jprolog.lang.PrologClassLoader;
import nars.jprolog.lang.ListTerm;
import nars.jprolog.lang.PrologControl;
import nars.jprolog.lang.Term;
import java.io.File;
/**
 * The <code>Compiler</code> class provides methods for 
 * translating Prolog programs into Java programs.
 *
 * The <code>Compiler</code> class supports the following compiler options.
 * All of them are set to <code>true</code> in default setting.
 * <ul>
 *   <li>Eliminate disjunctions
 *   <li>Arithmetic compilation
 *   <li>Inline expansion
 *   <li>Optimisation of recursive call
 *   <li>2nd. level indexing (<code>switch_on_hash</code>)
 * </ul>
 *
 * Let us show a sample session for translating a Prolog program
 * <code>$PLCAFEDIR/examples/prolog/list.pl</code> into Java.
 * The <code>list.pl</code> contains predicates
 * <code>append/3</code>, <code>nrev/2</code>, and <code>range/3</code>.
 * <ul>
 * <li>From Command line<br>
 * <pre>
 *    % java -cp $PLCAFEDIR/plcafe.jar jp.ac.kobe_u.cs.prolog.compiler.Compiler:$CLASSPATH $PLCAFEDIR/examples/prolog/list.pl 
 *    Prolog Cafe X.X.X (YYY)
 *    Copyright(C) 1997-200X M.Banbara and N.Tamura
 *    % ls
 *    PRED_append_3.java      PRED_nrev_2.java        PRED_range_3.java
 * </pre>
 * <li>From Java program<br>
 * <pre>
 *    import jp.ac.kobe_u.cs.prolog.compiler.Compiler;
 *    public class T {
 *        public static void main(String argv[]) {
 *            Compiler comp = new Compiler();
 *	    comp.prologToJava(argv[0], ".");
 *        }
 *    }
 * </pre>
 * <pre>
 *    % javac -classpath $PLCAFEDIR/plcafe.jar:$CLASSPATH T.java
 *    % java -classpath $PLCAFEDIR/plcafe.jar:$CLASSPATH T $PLCAFEDIR/examples/prolog/list.pl 
 *    % ls
 *    PRED_append_3.java      PRED_nrev_2.java        PRED_range_3.java
 * </pre>
 * </ul>
 *
 * It is noted that 
 * our Prolog-to-Java translator is originally witten in Prolog, and then bootstrapped.
 * Please see the following two Prolog programs in details.
 * <ul>
 *   <li><code>$PLCAFEDIR/src/compiler/pl2am.pl</code><br>
 *       Translates a Prolog program into a WAM-based intermediate code.
 *   <li><code>$PLCAFEDIR/src/compiler/am2j.pl</code><br>
 *       Translates a WAM-based intermediate code generated by <code>pl2am.pl</code> 
 *       into Java programs.
 * </ul>
 *
 * @author Mutsunori Banbara (banbara@kobe-u.ac.jp)
 * @author Naoyuki Tamura (tamura@kobe-u.ac.jp)
 * @version 1.2
 */
public class Compiler {
    /** Version information */
    public static String VERSION   = "Prolog Cafe 1.2.5 (mantis)";
    /** Copyright information */
    public static String COPYRIGHT = "Copyright(C) 1997-2009 M.Banbara and N.Tamura";

    /** Compiler option for eliminating disjunctions. Its initial value is <code>true</code> */
    protected boolean eliminateDisjunctions = true;
    /** Compiler option for arithmetic compilation. Its initial value is <code>true</code> */
    protected boolean arithmeticCompilation = true;
    /** Compiler option for inline expansion. Its initial value is <code>true</code> */
    protected boolean inlineExpansion       = true;
    /** Compiler option for optimising recursive call. Its initial value is <code>true</code> */
    protected boolean optimiseRecursiveCall = true;
    /** Compiler option for second-level indexing. Its initial value is <code>true</code> */
    protected boolean switchOnHash          = true;
    /** Non-standard option. Compiler option for closure generation. Its initial value is <code>false</code> */
    protected boolean generateClosure       = false;

    /** 
     * Translates a Prolog program into a WAM-based intermediate code. 
     *
     * @param _prolog an input Prolog file
     * @param _wam an output file for WAM-based intermediate code. 
     * @return <code>true</code> if succeeds, otherwise <code>false</code>.
    */
    public boolean prologToWAM(String _prolog, String _wam) {
	try {
	    if (! fileExists(_prolog)) {
		System.out.println("**ERROR: file " + _prolog + " does not exist");
		return false;
	    }
	    if (fileExists(_wam)) {
		System.out.println("**ERROR: file " + _wam + " already exists");
		return false;
	    }
	    // Create arguments
	    Term prolog = SymbolTerm.makeSymbol(_prolog);
	    Term wam    = SymbolTerm.makeSymbol(_wam);
	    Term op     = Prolog.Nil;
	    if (eliminateDisjunctions)
		op = new ListTerm(SymbolTerm.makeSymbol("ed"), op);
	    if (arithmeticCompilation)
		op = new ListTerm(SymbolTerm.makeSymbol("ac"), op);
	    if (inlineExpansion)
		op = new ListTerm(SymbolTerm.makeSymbol("ie"), op);
	    if (optimiseRecursiveCall)
		op = new ListTerm(SymbolTerm.makeSymbol("rc"), op);
	    if (switchOnHash)
		op = new ListTerm(SymbolTerm.makeSymbol("idx"), op);
	    if (generateClosure)
		op = new ListTerm(SymbolTerm.makeSymbol("clo"), op);
	    Term[] args = {new ListTerm(prolog, new ListTerm(wam, new ListTerm(op, Prolog.Nil)))};
	    // Create predicate
	    PrologClassLoader pcl = new PrologClassLoader(getClass().getClassLoader());
        Class clazz = pcl.loadPredicateClass("jp.ac.kobe_u.cs.prolog.compiler.pl2am", "pl2am", 1, true);
	    Predicate code = (Predicate)(clazz.newInstance());
	    // Translate Prolog into WAM
	    PrologControl p = new PrologControl(pcl);
	    p.setPredicate(code, args);
	    //	    System.out.println(code);
	    return p.execute(code, args);
	} catch (Exception e){
	    e.printStackTrace();
	}
	return false;
    }

    /** 
     * Translates WAM-based intermediate code into Java programs.
     *
     * @param _wam an input file for WAM-based intermediate code. 
     * @param _dir a destination directory for java files. The directory must already exist.
     * @return <code>true</code> if succeeds, otherwise <code>false</code>.
     * @see #prologToWAM(String, String)
    */
    public boolean wamToJava(String _wam, String _dir) {
	try {
	    if (! fileExists(_wam)) {
		System.out.println("**ERROR: file " + _wam + " does not exist");
		return false;
	    }
	    if (! fileExists(_dir)) {
		System.out.println("**ERROR: directory " + _dir + " does not exist");
		return false;
	    }
	    // Create arguments
	    Term wam    = SymbolTerm.makeSymbol(_wam);
	    Term dir    = SymbolTerm.makeSymbol(_dir);
	    Term[] args = {new ListTerm(wam, new ListTerm(dir, Prolog.Nil))};
	    // Create predicate
	    //	    Class clazz = PredicateEncoder.getClass("jp.ac.kobe_u.cs.prolog.compiler.am2j", "am2j", 1);
        PrologClassLoader pcl = new PrologClassLoader(getClass().getClassLoader());
	    Class clazz = pcl.loadPredicateClass("jp.ac.kobe_u.cs.prolog.compiler.am2j", "am2j", 1, true);
	    Predicate code = (Predicate)(clazz.newInstance());
	    // Translate WAM into Java
	    PrologControl p = new PrologControl(pcl);
	    p.setPredicate(code, args);
	    //	    System.out.println(code);
	    return p.execute(code, args);
	} catch (Exception e){
	    e.printStackTrace();
	}
	return false;
    }

    /** 
     * Translates a Prolog program into Java programs.
     *
     * @param prolog an input Prolog file
     * @param dir a destination directory for java files. The directory must already exist.
     * @return <code>true</code> if succeeds, otherwise <code>false</code>.
     * @see #prologToWAM(String, String)
     * @see #wamToJava(String, String)
    */
    public boolean prologToJava(String prolog, String dir) {
	String wam = prolog + ".am";
	if (! prologToWAM(prolog, wam))
	    return false;
	if (! wamToJava(wam, dir))
	    return false;
	try {
	    File f = new File(wam);
	    if (f.exists())
		f.delete();
	} catch (SecurityException e) {}
	return true;
    }

    public static void main(String argv[]) {
	try {
	    System.err.println("\n" + VERSION); 
	    System.err.println(COPYRIGHT);
	    if (argv.length != 1) {
		usage();
		System.exit(999);
	    } 
	    Compiler comp = new Compiler();
	    if (! comp.prologToJava(argv[0], "."))
		System.exit(1);
	    System.exit(0);
	} catch (Exception e){
	    e.printStackTrace();
	    System.exit(1);
	}
    }

    protected static boolean fileExists(String _file) {
	try {
	    File file = new File(_file);
	    return file.exists();
	} catch (SecurityException e) {}
	return false;
    }

    /** Shows usage */
    protected static void usage() {
	String s = "Usage:\n";
	s += "java -cp $PLCAFEDIR/plcafe.jar";
	s += " jp.ac.kobe_u.cs.prolog.compiler.Compiler prolog_file\n";
	System.out.println(s);
    }

    /** 
     * Returns the boolean value of <code>eliminateDisjunctions</code>.
     * @see #eliminateDisjunctions
    */
    public boolean getEliminateDisjunctions() { return eliminateDisjunctions; }
    /** 
     * The <code>eliminateDisjunctions</code> field is set to <code>b</code>.
     * @see #eliminateDisjunctions
    */
    public void setEliminateDisjunctions(boolean b) { eliminateDisjunctions = b; }

    /** 
     * Returns the boolean value of <code>arithmeticCompilation</code>.
     * @see #arithmeticCompilation
    */
    public boolean getArithmeticCompilation() { return arithmeticCompilation; }
    /** 
     * The <code>arithmeticCompilation</code> field is set to <code>b</code>.
     * @see #arithmeticCompilation
    */
    public void setArithmeticCompilation(boolean b) { arithmeticCompilation = b; }

    /** 
     * Returns the boolean value of <code>inlineExpansion</code>.
     * @see #inlineExpansion
    */
    public boolean getInlineExpansion() { return inlineExpansion; }
    /** 
     * The <code>inlineExpansion</code> field is set to <code>b</code>.
     * @see #inlineExpansion
    */
    public void setInlineExpansion(boolean b) { inlineExpansion = b; }

    /** 
     * Returns the boolean value of <code>optimiseRecursiveCall</code>.
     * @see #optimiseRecursiveCall
    */
    public boolean getOptimiseRecursiveCall() { return optimiseRecursiveCall; }
    /** 
     * The <code>optimiseRecursiveCall</code> field is set to <code>b</code>.
     * @see #optimiseRecursiveCall
    */
    public void setOptimiseRecursiveCall(boolean b) { optimiseRecursiveCall = b; }

    /** 
     * Returns the boolean value of <code>switchOnHash</code>.
     * @see #switchOnHash
    */
    public boolean getSwitchOnHash() { return switchOnHash; }
    /** 
     * The <code>switchOnHash</code> field is set to <code>b</code>.
     * @see #switchOnHash
    */
    public void setSwitchOnHash(boolean b) { switchOnHash = b; }

    /** 
     * Returns the boolean value of <code>generateClosure</code>.
     * @see #generateClosure
    */
    public boolean getGenerateClosure() { return generateClosure; }
    /** 
     * The <code>generateClosure</code> field is set to <code>b</code>.
     * @see #generateClosure
    */
    public void setGenerateClosure(boolean b) { generateClosure = b; }
}
