package cool.muyucloud.netherlink.link;

import net.minecraft.network.chat.Component;

public enum LinkBackendId {
    MOJ_26_2_S8(Component.translatable("netherlink.backend.moj_26_2_s8")),
    NLI_V1(Component.translatable("netherlink.backend.nli_v1"));

    private final Component component;

    LinkBackendId(Component component) {
        this.component = component;
    }

    public Component component() {
        return this.component;
    }
}
