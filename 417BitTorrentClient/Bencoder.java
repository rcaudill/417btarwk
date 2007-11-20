package example;
/*
 * Copyright 2006 Robert Sterling Moore II This computer program is free
 * software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version. This
 * computer program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details. You should have received a copy of the GNU General Public License
 * along with this computer program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */


import java.io.*;
import java.util.*;

/**
 * The <code>Bencoder</code> class contains operations needed to
 * <em>bencode</em> and bdecode objects used in the <a
 * href="http://http://www.bittorrent.org/protocol.html"> BitTorrent protocol</a>.
 * <p>
 * The BitTorrent protocol specifies 4 object types: <em>signed integers</em>,
 * <em>lists</em>, <em>dictionaries</em> and <em>byte strings</em>
 * bencoded data types which are mapped onto {@link Number}, {@link List},
 * {@link Map}, and {@link String} encoded in UTF-8, respectively.
 * 
 * @author Chris Lauderdale
 * @version 1.0
 */
final class Bencoder
{
	private static final class IRef
	{
		public int i;

		public ByteArrayOutputStream infoBytes = null;

		public IRef(int x)
		{
			super();
			i = x;
		}

		public void append(byte[] b, int st, int len)
		{
			if (infoBytes != null)
				infoBytes.write(b, st, len);
		}

		public void append(char b)
		{
			if (infoBytes != null)
				infoBytes.write(b);
		}

		public ByteArrayOutputStream createBAOS()
		{
			return infoBytes = new ByteArrayOutputStream();
		}
	}

	static final Class INTEGER_CLASS = Long.class;

	static final Class BYTE_STRING_CLASS = byte[].class;

	static final Class LIST_CLASS = List.class;

	static final Class DICTIONARY_CLASS = Map.class;

	static final Object INFO_BYTES = new Object();

	/**
	 * Bdecodes an object from an InputStream. The first <code>byte</code>
	 * returned by <code>is</code> must be the beginning of the bencoded
	 * object.
	 * 
	 * @param is
	 *            the input stream from which to bdecode an object
	 * @return the object represented by the bencoded <code>byte[]</code>
	 */
	static Object bdecode(InputStream is) throws IOException
	{
		byte[] response = new byte[8192];
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int amt;

		while ((amt = is.read(response)) >= 0)
		{
			if (amt == 0)
				Thread.yield();
			else
				baos.write(response);
		}

		return bdecode(baos.toByteArray());
	}

	/**
	 * Bdecodes an object from a <code>byte[]</code>. <code>bytes[0]</code>
	 * must be the beginning of the bencoded object.
	 * 
	 * @param bytes
	 *            the <code>byte[]</code> from which to bdecode an object
	 * @return the object represented by <code>bytes</code>
	 * @throws IndexOutOfBoundsException
	 */
	static Object bdecode(byte[] bytes)
	{
		if (bytes.length < 1)
			return null;
		return bdecode(bytes, 0);
	}

	/**
	 * Bdecodes an bencoded object contained in <code>bytes</code> specifying
	 * the <code>byte[]</code> from which to bdecode an object and the offset
	 * within <code>bytes</code> at which the object begins.
	 * <code>start</code> such that 0 &le; <code>start</code> &lt; <code>bytes.length</code>.
	 * 
	 * @param bytes
	 *            the <code>byte[]</code> from which to bdeocde an object
	 * @param start
	 *            the index within <code>bytes</code> that marks the beginning
	 *            of the object to be bdecoded
	 * @return the object bencoded beginning at <code>bytes[start]</code>
	 * @throws IndexOutOfBoundsException
	 */
	static Object bdecode(byte[] bytes, int start)
	{
		return bdecode(bytes, start, new IRef(0));
	}

	/**
	 * Bdecodes a <code>byte[]</code> containing a bencoded object beginning
	 * at index <code>start</code>. TODO: Something about ind here.
	 * 
	 * @param bytes
	 *            the <code>byte[]</code> containing the bencoded object
	 * @param start
	 *            the index in <code>bytes</code> that marks the beginning of
	 *            the bencoded object.
	 * @param ind contains the bytes of the bencoded <em>info dictionary</em>
	 * that have been encountered so far
	 * @return the object bencoded beginning at <code>byte[start]</code>
	 */
	private static Object bdecode(byte[] bytes, int start, IRef ind)
	{
		if (bytes[start] == 'i')
			return decodeInt(bytes, start, ind);
		if (bytes[start] == 'l')
			return decodeList(bytes, start, ind);
		if (bytes[start] == 'd')
			return decodeDict(bytes, start, ind);
		if (bytes[start] < '0' || bytes[start] > '9')
			throw new IllegalArgumentException("Invalid bencoded byte stream.");
		return decodeBytes(bytes, start, ind);
	}

	/**
	 * Bdecodes a single signed integer. <code>bytes[start]</code> is the 
	 * first byte of the bencoded integer.
	 * 
	 *  @param bytes the <code>byte[]</code> storing the bencoded integer
	 *  @param start the index in <code>bytes</code> that marks the beginning
	 *  of the bencoded integer
	 *  @param ind contains the bytes of the bencoded <em>info dictionary</em>
	 *  that have been encountered so far
	 *  @return the <code>Integer</code> representation of the bencoded 
	 *  integer
	 */
	private static Integer decodeInt(byte[] bytes, int start, IRef ind)
	{
		for (int i = start + 1; i < bytes.length; i++)
			if (bytes[i] == 'e')
			{
				final Integer rv = Integer.decode(new String(bytes, (byte) 0,
						start + 1, i - (start + 1)));
				ind.i = i + 1;
				ind.append(bytes, start, ind.i - start);
				return rv;
			}
		throw new IllegalArgumentException(
				"Invalid bencoded byte stream: Bad integer");
	}
	
	/**
	 * Bdecodes a bencoded list object beginning at <code>bytes[start]</code>.
	 * 
	 * @param bytes the <code>byte[]</code> storing the bencoded list
	 * @param start the index in <code>bytes</code> that marks the beginning
	 * of the list
	 * @param ind contains the bytes of the bencoded <em>info dictionary</em>
	 * that have been encountered so far
	 * @return the <code>List</code> representation of the bencoded list
	 */
	private static List decodeList(byte[] bytes, int start, IRef ind)
	{
		final List rv = new LinkedList();
		ind.append('l');
		++start;
		while (bytes[start] != 'e')
		{
			rv.add(bdecode(bytes, start, ind));
			start = ind.i;
		}
		ind.append('e');
		++ind.i;
		return rv;
	}

	/**
	 * Bdecodes a bencoded dictionary object beginning at 
	 * <code>bytes[start]</code>.
	 * 
	 * @param bytes the <code>byte[]</code> storing the bencoded dictionary
	 * @param start the index in <code>bytes</code> that marks the beginning
	 * of the dictionary
	 * @param ind contains the bytes of the bencoded <em>info dictionary</em>
	 * that have been encountered so far
	 * @return the <code>Map</code> representation of the dictionary beginning
	 * at <code>bytes[start]</code>
	 */
	private static Map decodeDict(byte[] bytes, int start, IRef ind)
	{
		final Map rv = new HashMap();
		boolean haveInfoBytes = false;
		ind.append('d');
		++start;
		while (bytes[start] != 'e')
		{
			byte[] f = decodeBytes(bytes, start, ind);
			start = ind.i;
			final String strf = new String(f, 0);
			if (strf.equalsIgnoreCase("info") && ind.infoBytes == null)
			{
				ind.createBAOS();
				haveInfoBytes = true;
			}
			rv.put(strf, bdecode(bytes, start, ind));
			start = ind.i;
		}
		++ind.i;
		if (haveInfoBytes)
		{
			rv.put(INFO_BYTES, ind.infoBytes.toByteArray());
			ind.infoBytes = null;
		}
		ind.append('e');
		return rv;
	}

	/**
	 * Bdecodes a bencoded byte string beginning at <code>bytes[start]</code>.
	 * 
	 * @param bytes the <code>byte[]</code> storing the bencoded byte string
	 * @param start the index in <code>bytes</code> that marks the beginning of
	 * the dictionary
	 * @param ind contains the bytes of the bencoded <em>info dictionary</em>
	 * that have been encountered so far
	 * @return the <code>byte[]</code> representation of the byte string
	 * beginning at <code>bytes[start]</code>
	 */
	private static byte[] decodeBytes(byte[] bytes, int start, IRef ind)
	{
		for (int i = start; i < bytes.length; i++)
			if (bytes[i] == ':')
			{
				final byte[] rv = new byte[Integer.parseInt(new String(bytes,
						(byte) 0, start, i - start))];
				System.arraycopy(bytes, ++i, rv, 0, rv.length);
				ind.i = i + rv.length;
				ind.append(bytes, start, ind.i - start);
				return rv;
			}
		throw new IllegalArgumentException(
				"Invalid bencoded byte stream: Invalid byte string");
	}

	/**
	 * Bencodes an object into a <code>byte[]</code>. If <code>o</code> is
	 * not a type specified in the BitTorrent protocol, then its string
	 * representation is bencoded as a byte string.
	 * 
	 * @param o
	 *            the object to be bencoded
	 * @return the bencoded <code>byte[]</code> representing <code>o</code>
	 */
	static byte[] bencode(Object o)
	{
		final ByteArrayOutputStream rv = new ByteArrayOutputStream();
		try
		{
			bencode(o, rv);
		}
		catch (IOException _)
		{}
		return rv.toByteArray();
	}

	/**
	 * Writes the bencoded form of <code>o</code> into <code>buf</code>. If
	 * <code>o</code> is not a type specified in the BitTorrent protocol, then
	 * its string representation is bencoded as a byte string.
	 * 
	 * @param o the object to be bencoded
	 * @param buf the <code>OutputStream</code> into which the bencoded form of
	 * <code>o</code> is written
	 */
	private static void bencode(Object o, OutputStream buf) throws IOException
	{
		if (o instanceof Iterable)
			encodeIterated(((Iterable) o).iterator(), buf);
		else if (o instanceof Iterator)
			encodeIterated((Iterator) o, buf);
		else if (o instanceof Map)
			encodeMap((Map) o, buf);
		else if (o instanceof Number)
			encodeNumber((Number) o, buf);
		else if (o instanceof byte[])
			encodeBytes((byte[]) o, buf);
		else
			try
			{
				encodeBytes(o.toString().getBytes("UTF-8"), buf);
			}
			catch (UnsupportedEncodingException _)
			{
				encodeBytes(o.toString().getBytes(), buf);
			}
	}

	/**
	 * Writes the bencoded form of the <code>Iterator</code> <code>i</code>
	 * into <code>buf</code>.
	 * 
	 * @param o the <code>Iterator</code> object to be bencoded
	 * @param buf the <code>OutputStream</code> into which the bencoded form of
	 * <code>o</code> is written
	 */
	private static void encodeIterated(Iterator i, OutputStream buf)
			throws IOException
	{
		buf.write('l');
		while (i.hasNext())
		{
			bencode(i.next(), buf);
		}
		buf.write('e');
	}

	/**
	 * Writes the bencoded form of the <code>Map</code> <code>m</code> into
	 * <code>buf</code>
	 * 
	 * @param m the <code>Map</code> object to be bencoded
	 * @param buf the <code>OutputStream</code> into which the bencoded form of
	 * <code>m</code> is written
	 */
	private static void encodeMap(Map m, OutputStream buf) throws IOException
	{
		buf.write('d');
		for (Iterator i = m.keySet().iterator(); i.hasNext();)
		{
			final Object o = i.next();
			final Object p = m.get(o);
			if (p == null)
				continue;
			byte[] bytes;
			if (o instanceof byte[])
				bytes = (byte[]) o;
			else
				try
				{
					bytes = o.toString().getBytes("UTF-8");
				}
				catch (UnsupportedEncodingException _)
				{
					bytes = o.toString().getBytes();
				}
			encodeBytes(bytes, buf);
			bencode(p, buf);
		}
		buf.write('e');
	}

	/**
	 * Writes the bencoded form of the <code>Number</code> <code>n</code> into
	 * <code>buf</code>
	 * 
	 * @param n the <code>Number</code> object to be bencoded
	 * @param buf the <code>OutputStream</code> into which the bencoded form of
	 * <code>n</code> is written
	 */
	private static void encodeNumber(Number n, OutputStream buf)
			throws IOException
	{
		buf.write('i');
		buf.write(Long.toString(n.longValue()).getBytes());
		buf.write('e');
	}
	
	/**
	 * Writes the bencoded form of the <code>byte[]</code> <code>b</code> into
	 * <code>buf</code>
	 * 
	 * @param b the <code>byte[]</code> object to be hashed
	 * @param buf the <code>OutputStream</code> into which the bencoded form of
	 * <code>n</code> is written
	 */
	private static void encodeBytes(byte[] b, OutputStream buf)
			throws IOException
	{
		buf.write(Integer.toString(b.length).getBytes());
		buf.write(':');
		buf.write(b);
	}
}
