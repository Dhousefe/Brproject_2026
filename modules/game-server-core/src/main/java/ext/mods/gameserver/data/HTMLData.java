/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
* * L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
* * L2Brproject is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* General Public License for more details.
* * You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
* Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
* Our special thanks, Nattan Felipe, Diego Fonseca, Junin, ColdPlay, Denky, MecBew, Localhost, MundvayneHELLBOY, 
* SonecaL2, Eduardo.SilvaL2J, biLL, xpower, xTech, kakuzo, Tiagorosendo, Schuster, LucasStark, damedd
* as a contribution for the forum L2JBrasil.com
 */
package ext.mods.gameserver.data;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

import ext.mods.commons.logging.CLogger;

import ext.mods.Config;
import ext.mods.config.ConfigLanguage;
import ext.mods.gameserver.model.actor.Player;

public final class HTMLData extends AbstractLocaleData
{
	private static final CLogger LOGGER = new CLogger(HTMLData.class.getName());
	
	private final Map<Locale, Map<String, String>> _data = new HashMap<>();
	
	@Override
	public void load()
	{
		for (var locale : ConfigLanguage.LOCALES)
		{
			_data.put(locale, new ConcurrentHashMap<>());
			doLoad(locale);
		}
	}
	
	public void reload()
	{
		LOGGER.info("HTMLData: Recarregando base de HTMLs...");
		
		for (var locale : ConfigLanguage.LOCALES)
		{
			final Map<String, String> map = _data.get(locale);
			if (map != null)
				map.clear();
			doLoad(locale);
		}
	}

	/**
	 * Normaliza caminhos de arquivo para uma chave consistente no cache.
	 * Converte barras invertidas, remove prefixos relativos (./) e padroniza
	 * a raiz a partir de 'html/' prevenindo falhas de lookup por divergencia de prefixos.
	 *
	 * @param file Caminho do arquivo a normalizar
	 * @return Chave normalizada (ex: "html/script/quest/Q001_LettersOfLove/30048-01.htm")
	 */
	public static String normalizeKey(String file)
	{
		if (file == null)
			return null;
		
		String normalized = file.replace('\\', '/').trim();
		while (normalized.startsWith("./"))
			normalized = normalized.substring(2);
		
		final int htmlIdx = normalized.indexOf("html/");
		if (htmlIdx != -1)
			normalized = normalized.substring(htmlIdx);
		
		return normalized;
	}
	
	private void doLoad(Locale locale)
	{
		final Path localeBasePath = resolve(locale, "");
		if (!Files.exists(localeBasePath))
			return;

		final Map<String, String> localeMap = _data.get(locale);
		if (localeMap == null)
			return;

		final List<CompletableFuture<Void>> futures = new ArrayList<>();
		
		try
		{
			Files.walkFileTree(localeBasePath, new SimpleFileVisitor<Path>()
			{
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
				{
					final var fileKey = localeBasePath.relativize(file).toString().replace('\\', '/');
					if (Files.isDirectory(file) || !(fileKey.endsWith(".htm") || fileKey.endsWith(".html")))
						return FileVisitResult.CONTINUE;
					
					futures.add(CompletableFuture.runAsync(() ->
					{
						try
						{
							final var content = readString(file);
							localeMap.put(fileKey, content);
						}
						catch (DataException e)
						{
							LOGGER.warn("HTMLData: Erro ao carregar arquivo [" + file + "]: " + e.getMessage());
						}
					}, ForkJoinPool.commonPool()));
					
					return FileVisitResult.CONTINUE;
				}
			});

			// Barreira sincrona de conclusao: assegura que 100% dos HTMLs estejam em memoria antes de prosseguir
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
			LOGGER.info("HTMLData: Carregados {} templates HTML para o locale [{}].", localeMap.size(), locale);
		}
		catch (IOException e)
		{
			LOGGER.warn("HTMLData: Falha ao escanear diretorio de locale: " + localeBasePath, e);
		}
	}
	
	@Override
	public String get(Locale locale, String key)
	{
		return getHtm(locale, key);
	}
	
	public String getHtm(Player player, String file)
	{
		return getHtm(player != null ? player.getLocale() : ConfigLanguage.DEFAULT_LOCALE, file);
	}
	
	public String getHtm(Locale locale, String file)
	{
		if (file == null || file.isEmpty())
			return "<html><body>Erro: Arquivo HTML nao especificado.</body></html>";

		if (locale == null)
			locale = ConfigLanguage.DEFAULT_LOCALE;

		final String key = normalizeKey(file);

		// 1. Busca direta na memoria do locale do jogador
		final Map<String, String> localeMap = _data.get(locale);
		String result = (localeMap != null) ? localeMap.get(key) : null;
		if (result != null)
			return result;

		// 2. Cache-Aside no disco do locale do jogador
		final Path diskPath = resolve(locale, key);
		if (Files.exists(diskPath) && !Files.isDirectory(diskPath))
		{
			try
			{
				result = readString(diskPath);
				if (localeMap != null)
					localeMap.put(key, result);
				return result;
			}
			catch (DataException e)
			{
				LOGGER.warn("HTMLData: Falha ao ler arquivo do disco sob demanda: " + diskPath, e);
			}
		}

		// 3. Fallback no locale padrao (en_US) caso o locale solicitado nao possua o arquivo
		final Locale defaultLocale = ConfigLanguage.DEFAULT_LOCALE;
		if (!locale.equals(defaultLocale))
		{
			// 3a. Memoria do locale padrao
			final Map<String, String> defaultMap = _data.get(defaultLocale);
			result = (defaultMap != null) ? defaultMap.get(key) : null;
			if (result != null)
				return result;

			// 3b. Disco do locale padrao
			final Path defaultDiskPath = resolve(defaultLocale, key);
			if (Files.exists(defaultDiskPath) && !Files.isDirectory(defaultDiskPath))
			{
				try
				{
					result = readString(defaultDiskPath);
					if (defaultMap != null)
						defaultMap.put(key, result);
					return result;
				}
				catch (DataException e)
				{
					LOGGER.warn("HTMLData: Falha ao ler arquivo do disco padrao sob demanda: " + defaultDiskPath, e);
				}
			}
		}

		return "<html><body>Not found file: " + file + "</body></html>";
	}
	
	public void put(Locale locale, String file, String content)
	{
		if (locale == null || file == null || content == null)
			return;

		final String key = normalizeKey(file);
		final Map<String, String> localeMap = _data.computeIfAbsent(locale, k -> new ConcurrentHashMap<>());
		localeMap.put(key, content);
	}
	
	public boolean exists(Player player, String file)
	{
		return exists(player != null ? player.getLocale() : ConfigLanguage.DEFAULT_LOCALE, file);
	}
	
	public boolean exists(Locale locale, String file)
	{
		if (file == null || file.isEmpty())
			return false;

		if (locale == null)
			locale = ConfigLanguage.DEFAULT_LOCALE;

		final String key = normalizeKey(file);

		// 1. Verificacao instantanea em memoria do locale
		final Map<String, String> localeMap = _data.get(locale);
		if (localeMap != null && localeMap.containsKey(key))
			return true;

		// 2. Verificacao no disco do locale solicitado
		final Path path = resolve(locale, key);
		if (Files.exists(path) && !Files.isDirectory(path))
			return true;

		// 3. Fallback no locale padrao
		final Locale defaultLocale = ConfigLanguage.DEFAULT_LOCALE;
		if (!locale.equals(defaultLocale))
		{
			final Map<String, String> defaultMap = _data.get(defaultLocale);
			if (defaultMap != null && defaultMap.containsKey(key))
				return true;

			final Path defaultPath = resolve(defaultLocale, key);
			return Files.exists(defaultPath) && !Files.isDirectory(defaultPath);
		}

		return false;
	}
	
	public static HTMLData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final HTMLData INSTANCE = new HTMLData();
	}
}