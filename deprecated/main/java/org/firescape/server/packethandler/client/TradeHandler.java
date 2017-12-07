package org.firescape.server.packethandler.client;

import org.apache.mina.common.IoSession;
import org.firescape.server.model.InvItem;
import org.firescape.server.model.Inventory;
import org.firescape.server.model.Player;
import org.firescape.server.model.World;
import org.firescape.server.net.Packet;
import org.firescape.server.net.RSCPacket;
import org.firescape.server.opcode.Command;
import org.firescape.server.opcode.Opcode;
import org.firescape.server.packethandler.PacketHandler;

import java.util.ArrayList;

public class TradeHandler implements PacketHandler {
  /**
   * World instance
   */
  public static final World world = World.getWorld();

  public void handlePacket(Packet p, IoSession session) throws Exception {
    Player player = (Player) session.getAttachment();
    int pID = ((RSCPacket) p).getID();
    Player affectedPlayer;
    if (busy(player)) {
      affectedPlayer = player.getWishToTrade();
      unsetOptions(player);
      unsetOptions(affectedPlayer);
      return;
    }
    if (pID == Opcode.getClient(204, Command.Client.CL_PLAYER_TRADE)) { // Sending trade request
      affectedPlayer = world.getPlayer(p.readShort());
      if (affectedPlayer == null ||
          !player.withinRange(affectedPlayer, 8) ||
          player.isTrading() ||
          player.tradeDuelThrottling()) {
        unsetOptions(player);
        return;
      }
      if (affectedPlayer == player) {
        player.setSuspiciousPlayer(true);
        System.out.println("Warning: " + player.getUsername() + " tried to trade to himself.");
        unsetOptions(player);
        return;
      }
      if ((affectedPlayer.getPrivacySetting(2) &&
           !affectedPlayer.isFriendsWith(org.firescape.server.util.DataConversions.hashToUsername(player.getUsernameHash()))) ||
          affectedPlayer.isIgnoring(org.firescape.server.util.DataConversions.hashToUsername(player.getUsernameHash()))) {
        player.getActionSender().sendMessage("This player has trade requests blocked.");
        return;
      }
      player.setWishToTrade(affectedPlayer);
      player.getActionSender()
            .sendMessage(affectedPlayer.isTrading() ? affectedPlayer.getUsername() +
                                                      " is " +
                                                      "already" +
                                                      " in a trade" : "Sending trade request");
      affectedPlayer.getActionSender().sendMessage(player.getUsername() + " wishes to trade with you");
      if (!player.isTrading() &&
          affectedPlayer.getWishToTrade() != null &&
          affectedPlayer.getWishToTrade().equals(player) &&
          !affectedPlayer.isTrading()) {
        player.setTrading(true);
        player.resetPath();
        player.resetAllExceptTrading();
        affectedPlayer.setTrading(true);
        affectedPlayer.resetPath();
        affectedPlayer.resetAllExceptTrading();
        player.getActionSender().sendTradeWindowOpen();
        affectedPlayer.getActionSender().sendTradeWindowOpen();
      }
      return;
    } else if (pID == Opcode.getClient(204, Command.Client.CL_TRADE_ACCEPT)) { // Trade accepted
      affectedPlayer = player.getWishToTrade();
      if (affectedPlayer == null || busy(affectedPlayer) || !player.isTrading() || !affectedPlayer.isTrading()) {
        // This
        // shouldn't
        // happen
        player.setSuspiciousPlayer(true);
        unsetOptions(player);
        unsetOptions(affectedPlayer);
        return;
      }
      player.setTradeOfferAccepted(true);
      player.getActionSender().sendTradeAcceptUpdate();
      affectedPlayer.getActionSender().sendTradeAcceptUpdate();
      if (affectedPlayer.isTradeOfferAccepted()) {
        player.getActionSender().sendTradeAccept();
        affectedPlayer.getActionSender().sendTradeAccept();
      }
      return;
    } else if (pID == Opcode.getClient(204, Command.Client.CL_TRADE_CONFIRM_ACCEPT)) { // Confirm accepted
      affectedPlayer = player.getWishToTrade();
      if (affectedPlayer == null ||
          busy(affectedPlayer) ||
          !player.isTrading() ||
          !affectedPlayer.isTrading() ||
          !player.isTradeOfferAccepted() ||
          !affectedPlayer.isTradeOfferAccepted()) { // This
        // shouldn't
        // happen
        player.setSuspiciousPlayer(true);
        unsetOptions(player);
        unsetOptions(affectedPlayer);
        return;
      }
      player.setTradeConfirmAccepted(true);
      if (affectedPlayer.isTradeConfirmAccepted()) {
        ArrayList<InvItem> myOffer = player.getTradeOffer();
        ArrayList<InvItem> theirOffer = affectedPlayer.getTradeOffer();
        int myRequiredSlots = player.getInventory().getRequiredSlots(theirOffer);
        int myAvailableSlots = (30 - player.getInventory().size()) + player.getInventory().getFreedSlots(myOffer);
        int theirRequiredSlots = affectedPlayer.getInventory().getRequiredSlots(myOffer);
        int theirAvailableSlots = (30 - affectedPlayer.getInventory().size()) +
                                  affectedPlayer.getInventory().getFreedSlots(theirOffer);
        if (theirRequiredSlots > theirAvailableSlots) {
          player.getActionSender().sendMessage("The other player does not have room to accept your items.");
          affectedPlayer.getActionSender().sendMessage("You do not have room in your inventory to hold those items.");
          unsetOptions(player);
          unsetOptions(affectedPlayer);
          return;
        }
        if (myRequiredSlots > myAvailableSlots) {
          player.getActionSender().sendMessage("You do not have room in your inventory to hold those items.");
          affectedPlayer.getActionSender().sendMessage("The other player does not have room to accept your items.");
          unsetOptions(player);
          unsetOptions(affectedPlayer);
          return;
        }
        for (InvItem item : myOffer) {
          InvItem affectedItem = player.getInventory().get(item);
          if (affectedItem == null) {
            player.setSuspiciousPlayer(true);
            unsetOptions(player);
            unsetOptions(affectedPlayer);
            return;
          }
          if (affectedItem.isWielded()) {
            affectedItem.setWield(false);
            player.updateWornItems(
              affectedItem.getWieldableDef().getWieldPos(),
              player.getPlayerAppearance().getSprite(affectedItem.getWieldableDef().getWieldPos())
            );
          }
          player.getInventory().remove(item);
        }
        for (InvItem item : theirOffer) {
          InvItem affectedItem = affectedPlayer.getInventory().get(item);
          if (affectedItem == null) {
            affectedPlayer.setSuspiciousPlayer(true);
            unsetOptions(player);
            unsetOptions(affectedPlayer);
            return;
          }
          if (affectedItem.isWielded()) {
            affectedItem.setWield(false);
            affectedPlayer.updateWornItems(
              affectedItem.getWieldableDef().getWieldPos(),
              affectedPlayer.getPlayerAppearance().getSprite(affectedItem.getWieldableDef().getWieldPos())
            );
          }
          affectedPlayer.getInventory().remove(item);
        }
        for (InvItem item : myOffer) {
          affectedPlayer.getInventory().add(item);
        }
        for (InvItem item : theirOffer) {
          player.getInventory().add(item);
        }
        player.getActionSender().sendInventory();
        player.getActionSender().sendEquipmentStats();
        player.getActionSender().sendMessage("Trade completed.");
        affectedPlayer.getActionSender().sendInventory();
        affectedPlayer.getActionSender().sendEquipmentStats();
        affectedPlayer.getActionSender().sendMessage("Trade completed.");
        unsetOptions(player);
        unsetOptions(affectedPlayer);
      }
      return;
    } else if (pID == Opcode.getClient(204, Command.Client.CL_TRADE_DECLINE)) { // Trade declined
      affectedPlayer = player.getWishToTrade();
      if (affectedPlayer == null || busy(affectedPlayer) || !player.isTrading() || !affectedPlayer.isTrading()) {
        // This
        // shouldn't
        // happen
        player.setSuspiciousPlayer(true);
        unsetOptions(player);
        unsetOptions(affectedPlayer);
        return;
      }
      affectedPlayer.getActionSender().sendMessage(player.getUsername() + " has declined the trade.");
      unsetOptions(player);
      unsetOptions(affectedPlayer);
      return;
    } else if (pID == Opcode.getClient(204, Command.Client.CL_TRADE_ITEM_UPDATE)) { // Receive offered item data
      affectedPlayer = player.getWishToTrade();
      if (affectedPlayer == null ||
          busy(affectedPlayer) ||
          !player.isTrading() ||
          !affectedPlayer.isTrading() ||
          (player.isTradeOfferAccepted() && affectedPlayer.isTradeOfferAccepted()) ||
          player.isTradeConfirmAccepted() ||
          affectedPlayer.isTradeConfirmAccepted()) { // This shouldn't happen
        player.setSuspiciousPlayer(true);
        unsetOptions(player);
        unsetOptions(affectedPlayer);
        return;
      }
      player.setTradeOfferAccepted(false);
      player.setTradeConfirmAccepted(false);
      affectedPlayer.setTradeOfferAccepted(false);
      affectedPlayer.setTradeConfirmAccepted(false);
      player.getActionSender().sendTradeAcceptUpdate();
      affectedPlayer.getActionSender().sendTradeAcceptUpdate();
      Inventory tradeOffer = new Inventory();
      player.resetTradeOffer();
      int count = (int) p.readByte();
      for (int slot = 0; slot < count; slot++) {
        InvItem tItem = new InvItem(p.readShort(), p.readInt());
        if (tItem.getAmount() < 1) {
          player.setSuspiciousPlayer(true);
          continue;
        }
        tradeOffer.add(tItem);
      }
      for (InvItem item : tradeOffer.getItems()) {
        if (tradeOffer.countId(item.getID()) > player.getInventory().countId(item.getID())) {
          player.setSuspiciousPlayer(true);
          unsetOptions(player);
          unsetOptions(affectedPlayer);
          return;
        }
        player.addToTradeOffer(item);
      }
      player.setRequiresOfferUpdate(true);
      return;
    }
  }

  private boolean busy(Player player) {
    return player.isBusy() || player.isRanging() || player.accessingBank() || player.isDueling();
  }

  private void unsetOptions(Player p) {
    if (p == null) {
      return;
    }
    p.resetTrading();
  }

}