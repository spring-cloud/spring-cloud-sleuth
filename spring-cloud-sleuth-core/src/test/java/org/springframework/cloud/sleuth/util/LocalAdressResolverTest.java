package org.springframework.cloud.sleuth.util;

import io.netty.channel.local.LocalAddress;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.util.LocalAdressResolver.NetworkInterfaceWrapper;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.*;
import static org.mockito.BDDMockito.given;

/**
 * @author Matcin Wielgus
 */
@RunWith(MockitoJUnitRunner.class)
public class LocalAdressResolverTest {
	static byte[] LOCALHOST = new byte[] { 127, 0, 0, 1 };
	static byte[] LOCALHOSTIP6 = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			1 };
	static byte[] IP6 = new byte[] { -2, -128, 0, 0, 0, 0, 0, 0, 98, -92, 76, -1, -2, 95,
			-79, 88 };
	static byte[] A10_0_0_1 = new byte[] { 10, 0, 0, 1 };
	static byte[] A192_168_0_1 = new byte[] { (byte) 192, (byte) 168, 0, 1 };
	@Spy
	LocalAdressResolver resolver;

	@Test
	public void getLocalIp4Address_should_not_return_loopback_interfaces()
			throws Exception {
		Enumeration<NetworkInterfaceWrapper> interfaces = asEnumeration(
				mockInterface(true, true, LOCALHOST),
				mockInterface(false, true, A10_0_0_1));
		given(resolver.getNetworkInterfaces()).willReturn(interfaces);

		String localIp4AddressAsString = resolver.getLocalIp4AddressAsString();

		then(localIp4AddressAsString).isEqualTo("10.0.0.1");

	}

	@Test
	public void getLocalIp4AddressAs_should_not_return_loopback_interfaces()
			throws Exception {
		Enumeration<NetworkInterfaceWrapper> interfaces = asEnumeration(
				mockInterface(true, true, LOCALHOST),
				mockInterface(false, true, A10_0_0_1));
		given(resolver.getNetworkInterfaces()).willReturn(interfaces);

		int localIp4AddressAsInt = resolver.getLocalIp4AddressAsInt();

		then(localIp4AddressAsInt).isEqualTo(10 << 24 | 1);
	}

	@Test
	public void getLocalIp4AddressAsString_should_not_return_down_interfaces()
			throws Exception {
		Enumeration<NetworkInterfaceWrapper> interfaces = asEnumeration(
				mockInterface(false, false, A192_168_0_1),
				mockInterface(false, true, A10_0_0_1));
		given(resolver.getNetworkInterfaces()).willReturn(interfaces);

		String localIp4AddressAsString = resolver.getLocalIp4AddressAsString();

		then(localIp4AddressAsString).isEqualTo("10.0.0.1");

	}

	@Test
	public void getLocalIp4AddressAsInt_should_not_return_down_interfaces()
			throws Exception {
		Enumeration<NetworkInterfaceWrapper> interfaces = asEnumeration(
				mockInterface(false, false, A192_168_0_1),
				mockInterface(false, true, A10_0_0_1));
		given(resolver.getNetworkInterfaces()).willReturn(interfaces);

		int localIp4AddressAsInt = resolver.getLocalIp4AddressAsInt();

		then(localIp4AddressAsInt).isEqualTo(10 << 24 | 1);
	}

	@Test
	public void getLocalIp4AddressAsInt_should_not_return_ip6addresses()
			throws Exception {
		Enumeration<NetworkInterfaceWrapper> interfaces = asEnumeration(
				mockInterface(true, true, LOCALHOSTIP6, LOCALHOST),
				mockInterface(false, true, IP6, A192_168_0_1),
				mockInterface(false, true, IP6, A10_0_0_1));
        given(resolver.getNetworkInterfaces()).willReturn(interfaces);

		int localIp4AddressAsInt = resolver.getLocalIp4AddressAsInt();

		then(localIp4AddressAsInt).isEqualTo(192 << 24 | 168 << 16 | 1);
	}

    @Test
    public void getLocalIp4AddressAsString_should_not_return_ip6addresses()
            throws Exception {
        Enumeration<NetworkInterfaceWrapper> interfaces = asEnumeration(
                mockInterface(true, true, LOCALHOSTIP6, LOCALHOST),
                mockInterface(false, true, IP6, A192_168_0_1),
                mockInterface(false, true, IP6, A10_0_0_1));
        given(resolver.getNetworkInterfaces()).willReturn(interfaces);

        String localIp4AddressAsString = resolver.getLocalIp4AddressAsString();

        then(localIp4AddressAsString).isEqualTo("192.168.0.1");
    }

	@Test
	public void getLocalIp4AddressAsString_should_return_localhost_When_no_interface_is_up()
			throws Exception {
		Enumeration<NetworkInterfaceWrapper> interfaces = asEnumeration(
				mockInterface(true, false, LOCALHOSTIP6, LOCALHOST),
				mockInterface(false, false, IP6, A192_168_0_1),
				mockInterface(false, false, IP6, A10_0_0_1));
		given(resolver.getNetworkInterfaces()).willReturn(interfaces);
		given(resolver.getLocalHost()).willReturn(InetAddress.getByAddress(new byte [] {1,1,1,1}));

		String localIp4AddressAsString = resolver.getLocalIp4AddressAsString();

		then(localIp4AddressAsString).isEqualTo("1.1.1.1");
	}

	@Test
	public void getLocalIp4AddressAsInt_should_return_localhost_When_no_interface_is_up()
			throws Exception {
		Enumeration<NetworkInterfaceWrapper> interfaces = asEnumeration(
				mockInterface(true, false, LOCALHOSTIP6, LOCALHOST),
				mockInterface(false, false, IP6, A192_168_0_1),
				mockInterface(false, false, IP6, A10_0_0_1));
		given(resolver.getNetworkInterfaces()).willReturn(interfaces);
		given(resolver.getLocalHost()).willReturn(InetAddress.getByAddress(new byte [] {1,1,1,1}));

		int localIp4AddressAsInt = resolver.getLocalIp4AddressAsInt();

		then(localIp4AddressAsInt).isEqualTo(1 << 24 | 1 << 16 | 1 << 8 | 1);
	}


	@Test
	public void getLocalIp4AddressAsString_should_return_localhost_When_unable_to_get_nics()
			throws Exception {
		given(resolver.getNetworkInterfaces()).willThrow(new SocketException());
		given(resolver.getLocalHost()).willReturn(InetAddress.getByAddress(new byte [] {1,1,1,1}));

		String localIp4AddressAsString = resolver.getLocalIp4AddressAsString();

		then(localIp4AddressAsString).isEqualTo("1.1.1.1");
	}

	@Test
	public void getLocalIp4AddressAsInt_should_return_localhost_When_unable_to_get_nics()
			throws Exception {

		given(resolver.getNetworkInterfaces()).willThrow(new SocketException());
		given(resolver.getLocalHost()).willReturn(InetAddress.getByAddress(new byte [] {1,1,1,1}));

		int localIp4AddressAsInt = resolver.getLocalIp4AddressAsInt();

		then(localIp4AddressAsInt).isEqualTo(1 << 24 | 1 << 16 | 1 << 8 | 1);
	}


	@Test
	public void getLocalIp4AddressAsString_should_return_localhost_When_unable_to_get_nics_and_localhost()
			throws Exception {
		given(resolver.getNetworkInterfaces()).willThrow(new SocketException());
		given(resolver.getLocalHost()).willThrow(new UnknownHostException());

		String localIp4AddressAsString = resolver.getLocalIp4AddressAsString();

		then(localIp4AddressAsString).isEqualTo("127.0.0.1");
	}

	@Test
	public void getLocalIp4AddressAsInt_should_return_localhost_When_unable_to_get_nics_and_localhost()
			throws Exception {

		given(resolver.getNetworkInterfaces()).willThrow(new SocketException());
		given(resolver.getLocalHost()).willThrow(new UnknownHostException());

		int localIp4AddressAsInt = resolver.getLocalIp4AddressAsInt();

		then(localIp4AddressAsInt).isEqualTo(127 << 24 | 1);
	}

	@Test
	public void getLocalIp4AddressAsInt_should_not_fail_when_using_unmocked_api()
			throws Exception {

		int localIp4AddressAsInt = resolver.getLocalIp4AddressAsInt();

		then(localIp4AddressAsInt).isGreaterThan(Integer.MIN_VALUE);
	}

	public Enumeration<NetworkInterfaceWrapper> asEnumeration(
			NetworkInterfaceWrapper... interfaces) {
		return Collections
				.<NetworkInterfaceWrapper> enumeration(Arrays.asList(interfaces));
	}

	public NetworkInterfaceWrapper mockInterface(boolean loopback, boolean up,
			byte[]... addresses) throws SocketException, UnknownHostException {
		NetworkInterfaceWrapper mock = Mockito.mock(NetworkInterfaceWrapper.class);
		Mockito.when(mock.isLoopback()).thenReturn(loopback);
		Mockito.when(mock.isUp()).thenReturn(up);
		Mockito.when(mock.getInetAddresses()).thenReturn(
				Collections.<InetAddress> enumeration(toAddresses(addresses)));
		return mock;
	}

	List<InetAddress> toAddresses(byte[]... addresses) throws UnknownHostException {
		List<InetAddress> ret = new ArrayList<>(addresses.length);
		for (byte[] address : addresses) {
			ret.add(InetAddress.getByAddress(address));
		}
		return ret;
	}

}