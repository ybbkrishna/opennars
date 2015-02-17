package nars;

import nars.core.NAR;
import nars.logic.entity.Term;
import nars.prolog.Prolog;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author me
 */
public class PrologContext {
    
    /** maps a NARS term to the coresponding prolog interpreter istance */
    public final Map<Term, Prolog> prologInterpreters = new WeakHashMap();
    
    // cache for all theories
    public final Map<String, CachedTheory> theoryCache = new HashMap();
    
    private final NAR nar;
    
    public PrologContext(NAR n) {
        this.nar = n;
        
        nar.addPlugin(new PrologTheoryStringOperator(this));
        nar.addPlugin(new PrologQueryOperator(this));
        nar.addPlugin(new PrologTheoryFileOperator(this));
        
    }
    
    // theoryInCache is a reference which gets the result, is the theory allready in the cache?
    public CachedTheory getCachedTheoryIfCached(String theoryName, AtomicBoolean theoryInCache) {
        theoryInCache.set( theoryCache.containsKey(theoryName) );
        if (theoryInCache.get()) {
            return theoryCache.get(theoryName);
        }
        
        return null;
    }
}