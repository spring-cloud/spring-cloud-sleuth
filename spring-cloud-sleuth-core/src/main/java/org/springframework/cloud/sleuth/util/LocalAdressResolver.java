package org.springframework.cloud.sleuth.util;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.Enumeration;

import org.apache.commons.logging.Log;

/**
 * Utility class for getting local Ip address from network interfaces.
 * Picks up first non loopback and up address.
 * @author Matcin Wielgus
 */
public class LocalAdressResolver {
	private static final Log log = org.apache.commons.logging.LogFactory
			.getLog(LocalAdressResolver.class);

	public String getLocalIp4AddressAsString() {
		InetAddress nicAddress = getLocalAddressFromNetworkInterfaces(Inet4Address.class);
		if (nicAddress == null) {
			try {
				return getLocalHost().getHostAddress();
			}
			catch (UnknownHostException e) {
				log.error(
						"Unable to get local ip address from InetAddress.getLocalHost()",
						e);
				return "127.0.0.1";
			}
		}
		else {
			return nicAddress.getHostAddress();
		}
	}

	public int asInt(InetAddress bytes) {
		return ByteBuffer.wrap(bytes.getAddress()).getInt();
	}

	public int getLocalIp4AddressAsInt() {
		InetAddress nicAddress = getLocalAddressFromNetworkInterfaces(Inet4Address.class);
		if (nicAddress == null) {
			try {
				return asInt(getLocalHost());
			}
			catch (UnknownHostException e) {
				log.error(
						"Unable to get local ip address from InetAddress.getLocalHost()",
						e);
				return 127 << 24 | 1;
			}
		}
		else {
			return asInt(nicAddress);
		}
	}

	InetAddress getLocalAddressFromNetworkInterfaces(
			Class<? extends InetAddress> expectedType) {
		InetAddress localAddress = null;
		try {
			Enumeration<NetworkInterfaceWrapper> n = getNetworkInterfaces();
			for (; n.hasMoreElements();) {
				NetworkInterfaceWrapper e = n.nextElement();
				if (!e.isLoopback() && e.isUp()) {
					Enumeration<InetAddress> a = e.getInetAddresses();
					for (; a.hasMoreElements();) {
						InetAddress addr = a.nextElement();
						if (addr != null && (expectedType == null
								|| expectedType.isAssignableFrom(addr.getClass())))
							return addr;
					}
				}
			}
			return null;
		}
		catch (Exception e) {
			log.error("Unable to get local ip address from network interfaces", e);
			return null;
		}

	}

	InetAddress getLocalHost() throws UnknownHostException {
		return InetAddress.getLocalHost();
	}

	/**
	 * NetworkInterface is final and has package scoped constructor, this makes testing
	 * possible
	 * @return
	 * @throws SocketException
	 */
	Enumeration<NetworkInterfaceWrapper> getNetworkInterfaces() throws SocketException {
		final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface
				.getNetworkInterfaces();
		return new Enumeration<NetworkInterfaceWrapper>() {
			@Override
			public boolean hasMoreElements() {
				return networkInterfaces.hasMoreElements();
			}

			@Override
			public NetworkInterfaceWrapper nextElement() {
				return new NetworkInterfaceWrapper(networkInterfaces.nextElement());
			}
		};
	}

	static class NetworkInterfaceWrapper {
		private final NetworkInterface networkInterface;

		NetworkInterfaceWrapper(NetworkInterface networkInterface) {
			this.networkInterface = networkInterface;
		}

		public Enumeration<InetAddress> getInetAddresses() {
			return this.networkInterface.getInetAddresses();
		}

		public boolean isUp() throws SocketException {
			return this.networkInterface.isUp();
		}

		public boolean isLoopback() throws SocketException {
			return this.networkInterface.isLoopback();
		}
	}
}
