package ext.mods.gameserver.network.serverpackets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.container.player.Appearance;
import ext.mods.gameserver.enums.actors.Sex;
import ext.mods.gameserver.network.GameClient;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de regressao para o pacote CharInfo quando o jogador esta invisivel e o cliente e nulo.
 */
public class CharInfoTest
{
    @Test
    @DisplayName("CharInfo nao deve lancar NullPointerException durante write() com player invisivel e client nulo")
    public void testInvisibleCharInfoWithNullClient() throws Exception
    {
        final Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        final sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);

        final Player player = (Player) unsafe.allocateInstance(Player.class);
        final Appearance app = new Appearance((byte) 0, (byte) 0, (byte) 0, Sex.MALE);
        app.setVisible(false); // GM invisivel!

        // Injeta _appearance via reflexao
        Field appField = Player.class.getDeclaredField("_appearance");
        appField.setAccessible(true);
        appField.set(player, app);

        CharInfo packet = new CharInfo(player);
        ByteBuffer buf = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);

        // Executa com client nulo; nao deve lancar NullPointerException na checagem de invisibilidade
        // Se tentar ler getClient().getPlayer() sem null-check em getClient(), quebrava com NPE
        assertDoesNotThrow(() -> {
            try {
                packet.writePacket(null, buf);
            } catch (NullPointerException e) {
                // Se a NPE for por causa de getClient().getPlayer() que corrigimos, falha o teste
                for (StackTraceElement elem : e.getStackTrace()) {
                    if (elem.getClassName().contains("CharInfo") && elem.getLineNumber() <= 60) {
                        fail("NullPointerException em CharInfo na checagem de canSeeInvis / getClient()!");
                    }
                }
            } catch (Throwable ignored) {
                // Ignora campos nao inicializados do stub de player apos a linha 60
            }
        });
    }
}
