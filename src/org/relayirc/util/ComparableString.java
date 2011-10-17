//-----------------------------------------------------------------------------
// $RCSfile: ComparableString.java,v $
// $Revision: 1.1.2.2 $
// $Author: snoopdave $
// $Date: 2001/03/27 11:36:49 $
//-----------------------------------------------------------------------------


package org.relayirc.util;

///////////////////////////////////////////////////////////////////////////

/**
 * Sortable string that implements IComparable.
 *
 * @see IComparable
 */
public class ComparableString implements IComparable {
    private String _str = null;

    public ComparableString(final String str) {
        _str = str;
    }

    public int compareTo(final IComparable other) {
        if (other instanceof ComparableString) {
            final ComparableString compString = (ComparableString) other;
            final String otherString = compString.getString();
            return _str.compareTo(otherString);
        } else return -1;
    }

    public String getString() {
        return _str;
    }

    public void setString(final String str) {
        _str = str;
    }
}
