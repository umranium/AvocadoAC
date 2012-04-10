package com.urremote.classifier.common;

import java.util.Comparator;

/**
 *
 * @author Umran
 */
public class StringComparator implements Comparator<String> {
	
	public static final StringComparator CASE_SENSITIVE_INSTANCE = new StringComparator(true);
	public static final StringComparator CASE_INSENSITIVE_INSTANCE = new StringComparator(false);

    private boolean caseSensitive;

    private StringComparator(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public int compare(String o1, String o2) {
        if (this.caseSensitive)
            return o1.compareTo(o2);
        else
            return o1.compareToIgnoreCase(o2);
    }

}
