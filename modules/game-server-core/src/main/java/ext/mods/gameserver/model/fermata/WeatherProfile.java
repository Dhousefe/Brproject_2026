package ext.mods.gameserver.model.fermata;

/**
 * Perfil imutável com os 13 parâmetros atmosféricos exigidos pelo motor de clima do Fermata.
 * Todos os valores são validados, finitos e limitados (clamped) às faixas seguras da especificação.
 */
public final class WeatherProfile
{
	// 0: none, 1: rain, 2: snow
	private final int _precipitation;
	private final double _intensity;
	private final double _windDirectionDegrees;
	private final double _windStrength;
	private final double _gustiness;
	private final double _cloudCover;
	private final double _lightningPerMinute;
	private final double _transitionSeconds;
	private final double _wettingSeconds;
	private final double _dryingSeconds;
	private final double _puddleAmount;
	private final double _snowAccumulationSeconds;
	private final double _snowMeltSeconds;
	
	// Presets pré-configurados
	public static final WeatherProfile CLEAR = new WeatherProfile(0, 0.0, 45.0, 0.1, 0.05, 0.05, 0.0, 10.0, 60.0, 60.0, 0.0, 60.0, 60.0);
	public static final WeatherProfile CLOUDY = new WeatherProfile(0, 0.0, 90.0, 0.35, 0.25, 0.85, 0.0, 15.0, 60.0, 60.0, 0.0, 60.0, 60.0);
	public static final WeatherProfile LIGHT_RAIN = new WeatherProfile(1, 0.35, 180.0, 0.45, 0.35, 0.75, 0.5, 15.0, 90.0, 120.0, 0.45, 60.0, 60.0);
	public static final WeatherProfile THUNDERSTORM = new WeatherProfile(1, 0.95, 240.0, 0.85, 0.90, 1.0, 12.0, 8.0, 20.0, 300.0, 0.95, 60.0, 60.0);
	public static final WeatherProfile LIGHT_SNOW = new WeatherProfile(2, 0.40, 315.0, 0.30, 0.20, 0.65, 0.0, 20.0, 60.0, 60.0, 0.0, 180.0, 180.0);
	public static final WeatherProfile BLIZZARD = new WeatherProfile(2, 1.00, 350.0, 0.95, 0.85, 1.0, 0.0, 10.0, 60.0, 60.0, 0.0, 45.0, 300.0);
	
	public WeatherProfile(int precipitation, double intensity, double windDirectionDegrees, double windStrength,
		double gustiness, double cloudCover, double lightningPerMinute, double transitionSeconds,
		double wettingSeconds, double dryingSeconds, double puddleAmount,
		double snowAccumulationSeconds, double snowMeltSeconds)
	{
		_precipitation = Math.max(0, Math.min(2, precipitation));
		_intensity = clamp(intensity, 0.0, 1.0);
		_windDirectionDegrees = normalizeDegrees(windDirectionDegrees);
		_windStrength = clamp(windStrength, 0.0, 1.0);
		_gustiness = clamp(gustiness, 0.0, 1.0);
		_cloudCover = clamp(cloudCover, 0.0, 1.0);
		_lightningPerMinute = clamp(lightningPerMinute, 0.0, 20.0);
		_transitionSeconds = clamp(transitionSeconds, 1.0, 120.0);
		_wettingSeconds = clamp(wettingSeconds, 5.0, 900.0);
		_dryingSeconds = clamp(dryingSeconds, 10.0, 1800.0);
		_puddleAmount = clamp(puddleAmount, 0.0, 1.0);
		_snowAccumulationSeconds = clamp(snowAccumulationSeconds, 15.0, 1800.0);
		_snowMeltSeconds = clamp(snowMeltSeconds, 15.0, 1800.0);
	}
	
	private static double clamp(double val, double min, double max)
	{
		if (!Double.isFinite(val))
		{
			return min;
		}
		return Math.max(min, Math.min(max, val));
	}
	
	private static double normalizeDegrees(double deg)
	{
		if (!Double.isFinite(deg))
		{
			return 0.0;
		}
		double d = deg % 360.0;
		return (d < 0.0) ? d + 360.0 : d;
	}
	
	public int getPrecipitation()
	{
		return _precipitation;
	}
	
	public double getIntensity()
	{
		return _intensity;
	}
	
	public double getWindDirectionDegrees()
	{
		return _windDirectionDegrees;
	}
	
	public double getWindStrength()
	{
		return _windStrength;
	}
	
	public double getGustiness()
	{
		return _gustiness;
	}
	
	public double getCloudCover()
	{
		return _cloudCover;
	}
	
	public double getLightningPerMinute()
	{
		return _lightningPerMinute;
	}
	
	public double getTransitionSeconds()
	{
		return _transitionSeconds;
	}
	
	public double getWettingSeconds()
	{
		return _wettingSeconds;
	}
	
	public double getDryingSeconds()
	{
		return _dryingSeconds;
	}
	
	public double getPuddleAmount()
	{
		return _puddleAmount;
	}
	
	public double getSnowAccumulationSeconds()
	{
		return _snowAccumulationSeconds;
	}
	
	public double getSnowMeltSeconds()
	{
		return _snowMeltSeconds;
	}
	
	public double[] toParamArray()
	{
		return new double[]
		{
			_intensity,
			_windDirectionDegrees,
			_windStrength,
			_gustiness,
			_cloudCover,
			_lightningPerMinute,
			_transitionSeconds,
			_wettingSeconds,
			_dryingSeconds,
			_puddleAmount,
			_snowAccumulationSeconds,
			_snowMeltSeconds
		};
	}
}
