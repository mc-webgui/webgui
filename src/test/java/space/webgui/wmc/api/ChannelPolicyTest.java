package space.webgui.wmc.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChannelPolicyTest {

    @Test
    void aPolicyThatNamesNobodyAllowsNobody() {
        ChannelPolicy policy = ChannelPolicy.builder().build();
        assertFalse(policy.allows("https://example.com"));
        assertFalse(policy.allows(null));
    }

    @Test
    void onlyTheNamedOriginsGetThrough() {
        ChannelPolicy policy = ChannelPolicy.builder().allowOrigin("app://shop").build();
        assertTrue(policy.allows("app://shop"));
        assertFalse(policy.allows("app://shop.evil"));
        assertFalse(policy.allows("https://shop"));
    }

    @Test
    void limitsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> ChannelPolicy.builder().perSecond(0));
        assertThrows(IllegalArgumentException.class, () -> ChannelPolicy.builder().maxPayload(-1));
    }
}
