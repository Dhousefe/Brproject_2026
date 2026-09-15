/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 */
package ext.mods.gameserver.handler.voicedcommandhandlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ext.mods.commons.pool.ThreadPool;
import ext.mods.gameserver.enums.GaugeColor;
import ext.mods.gameserver.enums.ZoneId;
import ext.mods.gameserver.enums.SpawnType;
import ext.mods.gameserver.handler.IVoicedCommandHandler;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.location.Location;
import ext.mods.gameserver.model.zone.type.RandomZone;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.ActionFailed;
import ext.mods.gameserver.network.serverpackets.ConfirmDlg;
import ext.mods.gameserver.network.serverpackets.MagicSkillUse;
import ext.mods.gameserver.network.serverpackets.SetupGauge;
import ext.mods.gameserver.data.xml.SysString;
import ext.mods.Crypta.RandomManager;
import ext.mods.commons.random.Rnd;
import ext.mods.gameserver.model.actor.player.DungeonState;


public class FarmZoneTeleport implements IVoicedCommandHandler {

     
    private static final String[] VOICED_COMMANDS = { "farm", "farmzone", "farm_teleport" };
    private static final String LAST_COMMAND_TAG = "farm_teleport";
    private static final int SOE_VISUAL_SKILL_ID = 2040;
    private static final int SOE_VISUAL_SKILL_LEVEL = 1;
    private static final int CAST_TIME_MS = 17000;

    private String getLocalizedString(Player player, String key) {
        Locale locale = player.getLocale();
        if (locale == null) {
            locale = Locale.ENGLISH;
        }
        return SysString.getInstance().get(locale, key);
    }

    @Override
    public boolean useVoicedCommand(String command, Player player, String args)
    {
        if (!command.equalsIgnoreCase("farm") && !command.equalsIgnoreCase("farmzone") && !command.equalsIgnoreCase("farm_teleport"))
            return false;

        if (player.isDead())
        {
            player.sendMessage("Você não pode usar este comando enquanto está morto.");
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (player.isInOlympiadMode())
        {
            player.sendMessage("Você não pode usar este comando durante as Olimpíadas.");
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (DungeonState.get(player) != null) {
            player.sendMessage(getLocalizedString(player, "12000"));
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (player.isInCombat())
        {
            player.sendMessage("Você não pode usar este comando em combate.");
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (player.getCast().isCastingNow() || player.isTeleporting())
        {
            player.sendMessage("Aguarde sua ação atual terminar.");
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        if (player.isInsideZone(ZoneId.PVP) || player.isInsideZone(ZoneId.SIEGE))
        {
            player.sendMessage("Você não pode usar este comando desta área.");
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        Object randomManager = RandomManager.getInstance();
        if (randomManager == null)
        {
            player.sendMessage("Sistema de eventos não disponível.");
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }
        
        Boolean isRunning = (Boolean) RandomManager.getInstance().isEventRunning();
        if (isRunning == null || !isRunning)
        {
            player.sendMessage("Nenhuma Farm Zone ativa no momento.");
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        final ConfirmDlg dlg = new ConfirmDlg(SystemMessageId.S1.getId());
        dlg.addString("Você deseja se teleportar para a Farm Zone?");
        dlg.addTime(30000);
        player.sendPacket(dlg);
        player.setLastCommand(LAST_COMMAND_TAG);
        return true;
    }

    public boolean handleConfirmation(Player player, boolean confirmed)
    {
        if (!LAST_COMMAND_TAG.equals(player.getLastCommand()))
            return false;

        player.setLastCommand(null);

        if (!confirmed)
            return false;

        if (player.isInCombat() || player.isDead() || player.isInOlympiadMode() || player.getCast().isCastingNow() || player.isTeleporting())
        {
            player.sendMessage("Teleporte cancelado. Sua condição mudou.");
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        Object randomManager = RandomManager.getInstance();

        if (randomManager == null)
        {
            player.sendMessage("Sistema de eventos não disponível.");
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        @SuppressWarnings("unchecked")
        List<RandomZone> activeZones = (List<RandomZone>) RandomManager.getInstance().getActiveZones();
        
        if (activeZones == null || activeZones.isEmpty())
        {
            player.sendMessage("Nenhuma Farm Zone ativa no momento.");
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }
        
        RandomZone targetZone = Rnd.get(activeZones);
        if (targetZone == null)
        {
            player.sendMessage("Erro ao selecionar zona do evento.");
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }
        
        
        Location loc = null;
        ext.mods.FarmEventRandom.holder.RandomZoneData zoneData = (ext.mods.FarmEventRandom.holder.RandomZoneData) RandomManager.getInstance().getZoneDataForZone(targetZone);
        if (zoneData != null && zoneData.getSpawnLocation() != null)
        {
            loc = zoneData.getSpawnLocation();
        }
        
        
        if (loc == null)
        {
            loc = targetZone.getRndSpawn(SpawnType.NORMAL);
        }
        
        if (loc == null)
        {
            @SuppressWarnings("unchecked")
            List<ext.mods.gameserver.model.spawn.Spawn> activeSpawns = (List<ext.mods.gameserver.model.spawn.Spawn>) RandomManager.getInstance().getActiveSpawns();
            
            if (activeSpawns != null && !activeSpawns.isEmpty())
            {
                List<ext.mods.gameserver.model.spawn.Spawn> zoneSpawns = new ArrayList<>();
                for (ext.mods.gameserver.model.spawn.Spawn spawn : activeSpawns)
                {
                    if (spawn != null && spawn.getNpc() != null)
                    {
                        ext.mods.gameserver.model.location.SpawnLocation spawnLoc = spawn.getSpawnLocation();
                        if (spawnLoc != null && targetZone.isInsideZone(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ()))
                        {
                            zoneSpawns.add(spawn);
                        }
                    }
                }
                
                if (!zoneSpawns.isEmpty())
                {
                    ext.mods.gameserver.model.spawn.Spawn selectedSpawn = Rnd.get(zoneSpawns);
                    if (selectedSpawn != null && selectedSpawn.getNpc() != null)
                    {
                        loc = new Location(selectedSpawn.getNpc().getX(), selectedSpawn.getNpc().getY(), selectedSpawn.getNpc().getZ());
                    }
                }
            }
        }
        
        if (loc == null)
        {
            Location returnLoc = targetZone.getReturnLocation();
            if (returnLoc != null && returnLoc.getX() != 0 && returnLoc.getY() != 0)
            {
                loc = returnLoc;
            }
        }
        
        if (loc == null)
        {
            player.sendMessage("Não foi possível encontrar um ponto de teleporte na Farm Zone.");
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
        }

        final Location finalLoc = loc;

        player.broadcastPacket(new MagicSkillUse(player, player, SOE_VISUAL_SKILL_ID, SOE_VISUAL_SKILL_LEVEL, CAST_TIME_MS, 0));
        player.sendPacket(new SetupGauge(GaugeColor.BLUE, CAST_TIME_MS));

        ThreadPool.schedule(() ->
        {
            if (player.isDead() || player.isInCombat() || player.isTeleporting())
                return;
            player.teleToLocation(finalLoc);
        }, CAST_TIME_MS);

        return true;
    }

    @Override
    public String[] getVoicedCommandList()
    {
        return VOICED_COMMANDS;
    }
}
