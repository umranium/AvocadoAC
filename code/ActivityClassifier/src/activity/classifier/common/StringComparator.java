package activity.classifier.common;

import java.util.Comparator;

/**
 *
 * @author Umran
 */
public class StringComparator implements Comparator<String> {

    private boolean caseSensitive;

    public StringComparator(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public int compare(String o1, String o2) {
        if (this.caseSensitive)
            return o1.compareTo(o2);
        else
            return o1.compareToIgnoreCase(o2);
    }

}
