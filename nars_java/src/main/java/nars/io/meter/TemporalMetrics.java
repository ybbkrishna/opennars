/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nars.io.meter;

import java.io.PrintStream;

/**
 *
 * @author me
 */
public class TemporalMetrics<Cell> extends Metrics<Double,Cell> {

    public TemporalMetrics(int historySize) {
        super(historySize);
    }

    
}