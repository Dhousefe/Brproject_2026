package ext.mods.loginserver.network.serverpackets;

/**
 * Pacote de rejeicao de autenticacao externa (0xFD 0x0001).
 * Estrutura de fio: [0xFD (C) | 0x0001 (H) | category (C)]
 *
 * Categorias:
 * 0x01: Discord login is off or not configured on this server
 * 0x02: No pending challenge, or it expired or does not match
 * 0x03: The proof failed verification
 * 0x04: The account for this identity exists but is not linked to it, or the identity is linked to another account
 * 0x05: Account creation failed
 */
public final class ExternalAuthRejected extends L2LoginServerPacket
{
	public static final ExternalAuthRejected REASON_NOT_AVAILABLE = new ExternalAuthRejected(0x01);
	public static final ExternalAuthRejected REASON_CHALLENGE_EXPIRED = new ExternalAuthRejected(0x02);
	public static final ExternalAuthRejected REASON_PROOF_INVALID = new ExternalAuthRejected(0x03);
	public static final ExternalAuthRejected REASON_LINK_CONFLICT = new ExternalAuthRejected(0x04);
	public static final ExternalAuthRejected REASON_CREATION_FAILED = new ExternalAuthRejected(0x05);
	
	private final int _category;
	
	public ExternalAuthRejected(int category)
	{
		_category = category;
	}
	
	@Override
	protected void write()
	{
		writeC(0xFD);
		writeH(0x0001);
		writeC(_category);
	}
}
