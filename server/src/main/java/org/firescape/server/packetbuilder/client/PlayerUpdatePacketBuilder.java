package org.firescape.server.packetbuilder.client;

import org.firescape.server.model.*;
import org.firescape.server.net.RSCPacket;
import org.firescape.server.packetbuilder.RSCPacketBuilder;

import java.util.List;

public class PlayerUpdatePacketBuilder {
  private Player playerToUpdate;

  /**
   * Sets the player to update
   */
  public void setPlayer(Player p) {
    playerToUpdate = p;
  }

  public RSCPacket getPacket() {
    List<Bubble> bubblesNeedingDisplayed = playerToUpdate.getBubblesNeedingDisplayed();
    List<ChatMessage> chatMessagesNeedingDisplayed = playerToUpdate.getChatMessagesNeedingDisplayed();
    List<Player> playersNeedingHitsUpdate = playerToUpdate.getPlayersRequiringHitsUpdate();
    List<Projectile> projectilesNeedingDisplayed = playerToUpdate.getProjectilesNeedingDisplayed();
    List<Player> playersNeedingAppearanceUpdate = playerToUpdate.getPlayersRequiringAppearanceUpdate();
    int updateSize = bubblesNeedingDisplayed.size() +
                     chatMessagesNeedingDisplayed.size() +
                     playersNeedingHitsUpdate.size() +
                     projectilesNeedingDisplayed.size() +
                     playersNeedingAppearanceUpdate.size();
    if (updateSize > 0) {
      RSCPacketBuilder updates = new RSCPacketBuilder();
      updates.setID(234);
      updates.addShort(updateSize);
      for (Bubble b : bubblesNeedingDisplayed) { // 0 - Draws item over players
        // head
        updates.addShort(b.getOwner().getIndex());
        updates.addByte((byte) 0);
        updates.addShort(b.getID());
      }
      for (ChatMessage cm : chatMessagesNeedingDisplayed) { // 1/6 - Player
        // talking
        updates.addShort(cm.getSender().getIndex());
        updates.addByte((byte) (cm.getRecipient() == null ? 1 : 6));
        updates.addByte((byte) cm.getLength());
        updates.addBytes(cm.getMessage());
      }
      for (Player p : playersNeedingHitsUpdate) { // 2 - Hitpoints update for
        // players, draws health bar
        // etc too
        updates.addShort(p.getIndex());
        updates.addByte((byte) 2);
        updates.addByte((byte) p.getLastDamage());
        updates.addByte((byte) p.getCurStat(3));
        updates.addByte((byte) p.getMaxStat(3));
      }
      for (Projectile p : projectilesNeedingDisplayed) { // 3/4 - Draws a
        // projectile
        Entity victim = p.getVictim();
        if (victim instanceof Npc) {
          updates.addShort(p.getCaster().getIndex());
          updates.addByte((byte) 3);
          updates.addShort(p.getType());
          updates.addShort(victim.getIndex());
        } else if (victim instanceof Player) {
          updates.addShort(p.getCaster().getIndex());
          updates.addByte((byte) 4);
          updates.addShort(p.getType());
          updates.addShort(victim.getIndex());
        }
      }
      for (Player p : playersNeedingAppearanceUpdate) { // 5 - Updates players
        // appearance, clothes,
        // skull, combat etc.
        PlayerAppearance appearance = p.getPlayerAppearance();
        updates.addShort(p.getIndex());
        updates.addByte((byte) 5);
        updates.addShort(p.getAppearanceID());
        updates.addLong(p.getUsernameHash());
        updates.addByte((byte) p.getWornItems().length);
        for (int i : p.getWornItems()) {
          updates.addByte((byte) i);
        }
        updates.addByte(appearance.getHairColour());
        updates.addByte(appearance.getTopColour());
        updates.addByte(appearance.getTrouserColour());
        updates.addByte(appearance.getSkinColour());
        updates.addByte((byte) p.getCombatLevel());
        updates.addByte((byte) (p.isSkulled() ? 1 : 0));
      }
      return updates.toPacket();
    }
    return null;
  }
}
