package cool.muyucloud.netherlink.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class NetherLinkFriendsScreen extends Screen {
    private static final Component TITLE = Component.translatable("netherlink.friends.title");
    private final Screen parent;
    private final boolean allowJoin;
    private ClientFriendService service;
    private EditBox addName;
    private Button refreshButton;
    private Button addButton;
    private Button joinButton;
    private Button removeButton;
    private Button acceptButton;
    private Button declineButton;
    private Button prevButton;
    private Button nextButton;
    private Component status = Component.translatable("netherlink.friends.loading").withStyle(ChatFormatting.GRAY);
    private List<ClientFriendService.Entry> entries = List.of();
    private int selected = -1;
    private int page;

    public NetherLinkFriendsScreen(Screen parent, boolean allowJoin) {
        super(TITLE);
        this.parent = parent;
        this.allowJoin = allowJoin;
    }

    @Override
    protected void init() {
        this.service = new ClientFriendService(this.minecraft);
        int left = this.width / 2 - 154;
        int top = 42;
        this.addName = this.addRenderableWidget(new EditBox(this.font, left, this.height - 56, 200, 20, Component.translatable("netherlink.friends.add")));
        this.addName.setHint(Component.translatable("netherlink.friends.add.hint"));
        this.addName.setMaxLength(32);
        this.refreshButton = this.addRenderableWidget(Button.builder(Component.translatable("netherlink.friends.refresh"), button -> this.refresh()).bounds(left, this.height - 30, 98, 20).build());
        this.addButton = this.addRenderableWidget(Button.builder(Component.translatable("netherlink.friends.add"), button -> this.addFriend()).bounds(left + 210, this.height - 56, 98, 20).build());
        this.joinButton = this.addRenderableWidget(Button.builder(Component.translatable("netherlink.friends.join"), button -> this.joinSelected()).bounds(left + 210, top, 98, 20).build());
        this.removeButton = this.addRenderableWidget(Button.builder(Component.translatable("netherlink.friends.remove"), button -> this.removeSelected()).bounds(left + 210, top + 24, 98, 20).build());
        this.acceptButton = this.addRenderableWidget(Button.builder(Component.translatable("netherlink.friends.accept"), button -> this.acceptSelected()).bounds(left + 210, top + 48, 98, 20).build());
        this.declineButton = this.addRenderableWidget(Button.builder(Component.translatable("netherlink.friends.decline"), button -> this.declineSelected()).bounds(left + 210, top + 72, 98, 20).build());
        this.prevButton = this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            this.page = Math.max(0, this.page - 1);
            this.selected = -1;
            this.updateButtons();
        }).bounds(left + 110, this.height - 30, 42, 20).build());
        this.nextButton = this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            this.page = Math.min(this.maxPage(), this.page + 1);
            this.selected = -1;
            this.updateButtons();
        }).bounds(left + 156, this.height - 30, 42, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).bounds(left + 210, this.height - 30, 98, 20).build());
        this.refresh();
        this.updateButtons();
    }

    private void refresh() {
        this.status = Component.translatable("netherlink.friends.loading").withStyle(ChatFormatting.GRAY);
        this.refreshButton.active = false;
        this.service.refresh().whenComplete((snapshot, error) -> this.minecraft.execute(() -> {
            this.refreshButton.active = true;
            if (error != null) {
                this.status = Component.translatable("netherlink.friends.error", error.getMessage()).withStyle(ChatFormatting.RED);
                return;
            }
            this.entries = snapshot.all();
            this.selected = -1;
            this.page = Math.min(this.page, this.maxPage());
            this.status = Component.translatable("netherlink.friends.loaded", this.entries.size()).withStyle(ChatFormatting.GRAY);
            this.updateButtons();
        }));
    }

    private void addFriend() {
        String name = this.addName.getValue().trim();
        if (!name.isEmpty()) {
            this.runFriendAction(this.service.add(name), "netherlink.friends.added");
        }
    }

    private void removeSelected() {
        ClientFriendService.Entry entry = this.selectedEntry();
        if (entry != null) {
            this.runFriendAction(this.service.remove(entry.profileId()), "netherlink.friends.removed");
        }
    }

    private void acceptSelected() {
        ClientFriendService.Entry entry = this.selectedEntry();
        if (entry != null) {
            this.runFriendAction(this.service.accept(entry.profileId()), "netherlink.friends.accepted");
        }
    }

    private void declineSelected() {
        ClientFriendService.Entry entry = this.selectedEntry();
        if (entry != null) {
            this.runFriendAction(entry.relationship() == ClientFriendService.Relationship.OUTGOING ? this.service.revoke(entry.profileId()) : this.service.decline(entry.profileId()), "netherlink.friends.declined");
        }
    }

    private void joinSelected() {
        ClientFriendService.Entry entry = this.selectedEntry();
        if (entry == null || entry.pmid() == null) {
            return;
        }
        this.status = Component.translatable("netherlink.friends.joining", entry.name()).withStyle(ChatFormatting.YELLOW);
        ClientJoinController.join(this.minecraft, entry.pmid()).whenComplete((ignored, error) -> this.minecraft.execute(() -> {
            if (error != null) {
                this.status = Component.translatable("netherlink.friends.join_failed", error.getMessage()).withStyle(ChatFormatting.RED);
            } else {
                this.status = Component.translatable("netherlink.friends.join_sent", entry.name()).withStyle(ChatFormatting.GRAY);
            }
        }));
    }

    private void runFriendAction(java.util.concurrent.CompletableFuture<ClientFriendService.ResultCode> action, String successKey) {
        this.status = Component.translatable("netherlink.friends.working").withStyle(ChatFormatting.GRAY);
        action.whenComplete((result, error) -> this.minecraft.execute(() -> {
            if (error != null) {
                this.status = Component.translatable("netherlink.friends.error", error.getMessage()).withStyle(ChatFormatting.RED);
            } else if (result == ClientFriendService.ResultCode.SUCCESS) {
                this.status = Component.translatable(successKey).withStyle(ChatFormatting.GREEN);
                this.refresh();
            } else {
                this.status = Component.translatable("netherlink.friends.result", result.name()).withStyle(ChatFormatting.RED);
            }
        }));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int left = this.width / 2 - 154;
        int y = 42;
        List<ClientFriendService.Entry> visible = this.visibleEntries();
        for (int i = 0; i < visible.size(); i++) {
            if (event.x() >= left && event.x() < left + 200 && event.y() >= y && event.y() < y + 22) {
                this.selected = this.page * this.pageSize() + i;
                this.updateButtons();
                return true;
            }
            y += 24;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int left = this.width / 2 - 154;
        graphics.centeredText(this.font, this.title, this.width / 2, 18, -1);
        int y = 42;
        List<ClientFriendService.Entry> visible = this.visibleEntries();
        for (int i = 0; i < visible.size(); i++) {
            int global = this.page * this.pageSize() + i;
            ClientFriendService.Entry entry = visible.get(i);
            int color = global == this.selected ? 0xFFFFE08A : 0xFFFFFFFF;
            graphics.text(this.font, Component.literal(entry.name()), left, y, color);
            graphics.text(this.font, statusText(entry), left, y + 10, 0xFFAAAAAA);
            y += 24;
        }
        if (visible.isEmpty()) {
            graphics.text(this.font, Component.translatable("netherlink.friends.empty").withStyle(ChatFormatting.GRAY), left, y, -1);
        }
        graphics.text(this.font, this.status, left, this.height - 72, -1);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private void updateButtons() {
        ClientFriendService.Entry entry = this.selectedEntry();
        this.joinButton.active = this.allowJoin && entry != null && entry.relationship() == ClientFriendService.Relationship.FRIEND && entry.joinable() && entry.pmid() != null && !ClientJoinController.hasOutgoingJoin();
        this.removeButton.active = entry != null && entry.relationship() == ClientFriendService.Relationship.FRIEND;
        this.acceptButton.active = entry != null && entry.relationship() == ClientFriendService.Relationship.INCOMING;
        this.declineButton.active = entry != null && entry.relationship() != ClientFriendService.Relationship.FRIEND;
        this.prevButton.active = this.page > 0;
        this.nextButton.active = this.page < this.maxPage();
    }

    private ClientFriendService.Entry selectedEntry() {
        return this.selected >= 0 && this.selected < this.entries.size() ? this.entries.get(this.selected) : null;
    }

    private List<ClientFriendService.Entry> visibleEntries() {
        int start = this.page * this.pageSize();
        int end = Math.min(this.entries.size(), start + this.pageSize());
        return start < end ? new ArrayList<>(this.entries.subList(start, end)) : List.of();
    }

    private int pageSize() {
        return Math.max(3, (this.height - 130) / 24);
    }

    private int maxPage() {
        return Math.max(0, (this.entries.size() - 1) / this.pageSize());
    }

    private static Component statusText(ClientFriendService.Entry entry) {
        String relationKey = switch (entry.relationship()) {
            case FRIEND -> "netherlink.friends.relation.friend";
            case INCOMING -> "netherlink.friends.relation.incoming";
            case OUTGOING -> "netherlink.friends.relation.outgoing";
        };
        Component status = switch (entry.status().toUpperCase(java.util.Locale.ROOT)) {
            case "ONLINE" -> Component.translatable("netherlink.friends.status.online");
            case "PLAYING_OFFLINE" -> Component.translatable("netherlink.friends.status.playing_offline");
            case "PLAYING_REALMS" -> Component.translatable("netherlink.friends.status.playing_realms");
            case "PLAYING_SERVER" -> Component.translatable("netherlink.friends.status.playing_server");
            case "PLAYING_HOSTED_SERVER" -> Component.translatable("netherlink.friends.status.playing_hosted_server");
            default -> Component.translatable("netherlink.friends.status.offline");
        };
        Component text = Component.empty()
            .append(Component.translatable(relationKey))
            .append(Component.literal(" / "))
            .append(status);
        if (entry.joinable()) {
            text = text.copy().append(Component.literal(" / ")).append(Component.translatable("netherlink.friends.status.joinable"));
        }
        return text;
    }
}
