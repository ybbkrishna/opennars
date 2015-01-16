/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package nars.operator.filter;

import nars.logic.NAL;
import nars.logic.NAL.DerivationFilter;
import nars.logic.entity.Sentence;
import nars.logic.entity.Task;

/**
<patham9_> desire value form the entire motivation management, what i did was allowing only input judgements and derived goals (no derived judgements) :D
<patham9_> only exception: allowed temporal induction derived judgements 
 */
public class DeriveOnlyDesired implements DerivationFilter {

    @Override
    public String reject(NAL nal, Task task, boolean revised, boolean single, Task parent, Sentence otherBelief) {
        if (!task.sentence.isGoal()) {
            if (task.sentence.isEternal())
                return "Not Goal";
        }
        return null;        
    }
    
}