package org.openarchive.telegram;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class RestoreRecoveryPolicyTest {
    private static final String A="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String B="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    @Test public void rollbackUnpublished(){
        assertEquals(RestoreRecoveryPolicy.State.UNPUBLISHED,RestoreRecoveryPolicy.state(Arrays.asList(A,B),Set.of("existing")));
    }
    @Test public void keepPublished(){
        assertEquals(RestoreRecoveryPolicy.State.COMMITTED,RestoreRecoveryPolicy.state(Arrays.asList(A,B),Set.of(A,B,"old_key")));
    }
    @Test public void failClosedOnPartialRegistry(){
        assertEquals(RestoreRecoveryPolicy.State.AMBIGUOUS,RestoreRecoveryPolicy.state(Arrays.asList(A,B),Set.of(A)));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectDuplicateKeys(){
        RestoreRecoveryPolicy.state(Arrays.asList(A,A),Set.of(A));
    }
    @Test public void rejectPathEscapes(){
        assertFalse(RestoreRecoveryPolicy.safeStage("restore-../data"));
        assertFalse(RestoreRecoveryPolicy.safeAsset("../../old.jpg"));
        assertFalse(RestoreRecoveryPolicy.safeKey(A+"/old"));
    }
}
