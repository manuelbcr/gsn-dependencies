package ch.epfl.gsn.wrappers.backlog.statistics;

import ch.epfl.gsn.wrappers.BackLogStatsWrapper;

import java.io.IOException;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class StatisticsMain {

	protected final transient Logger logger = LoggerFactory.getLogger(StatisticsMain.class);

	private static Map<String, DeploymentStatistics> deploymentToDeploymentStatsList = Collections
			.synchronizedMap(new Hashtable<String, DeploymentStatistics>());

	private static StatisticsMain singletonObject = null;

	private StatisticsMain() {
	}

	public synchronized static DeploymentStatistics getDeploymentStatsInstance(String deploymentName,
			BackLogStatsWrapper statswrapper) {
		if (singletonObject == null) {
			singletonObject = new StatisticsMain();
		}

		if (!deploymentToDeploymentStatsList.containsKey(deploymentName)) {
			deploymentToDeploymentStatsList.put(deploymentName, new DeploymentStatistics(statswrapper));
		} else {
			deploymentToDeploymentStatsList.get(deploymentName).setStatsWrapper(statswrapper);
		}

		return deploymentToDeploymentStatsList.get(deploymentName);
	}

	public synchronized static CoreStationStatistics getCoreStationStatsInstance(String deploymentName,
			String coreStationAddress) throws IOException {
		if (singletonObject == null) {
			singletonObject = new StatisticsMain();
		}

		if (!deploymentToDeploymentStatsList.containsKey(deploymentName)) {
			deploymentToDeploymentStatsList.put(deploymentName, new DeploymentStatistics(null));
		}

		return deploymentToDeploymentStatsList.get(deploymentName).newStatisticsClass(coreStationAddress);
	}

	public static void connectionStatusChanged(String deploymentName, int deviceId) throws IOException {
		if (singletonObject == null) {
			singletonObject = new StatisticsMain();
		}

		if (!deploymentToDeploymentStatsList.containsKey(deploymentName)) {
			throw new IOException("deployment " + deploymentName + " does not exist in the statistics");
		}

		BackLogStatsWrapper deplstats = deploymentToDeploymentStatsList.get(deploymentName).getStatsWrapper();
		if (deplstats != null) {
			deplstats.connectionStatusChanged(deviceId);
		}

	}

	public synchronized static void removeCoreStationStatsInstance(String deploymentName, String coreStationAddress)
			throws IOException {
		if (singletonObject == null) {
			singletonObject = new StatisticsMain();
		}

		if (!deploymentToDeploymentStatsList.containsKey(deploymentName)) {
			throw new IOException("deployment " + deploymentName + " does not exist in the statistics");
		}

		deploymentToDeploymentStatsList.get(deploymentName).removeCoreStationStatsInstance(coreStationAddress);
	}
}
