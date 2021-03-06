/*
 * The MIT License
 * Copyright © 2018 Phillip Schichtel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tel.schich.javacan;

/**
 * This class provides constants and methods to work with ISOTP addresses.
 */
public class IsotpAddress {

    /**
     * The relative destination address for ECU 1
     */
    public static final int DESTINATION_ECU_1              = 0x00;

    /**
     * The relative destination address for ECU 2
     */
    public static final int DESTINATION_ECU_2              = 0x01;

    /**
     * The relative destination address for ECU 3
     */
    public static final int DESTINATION_ECU_3              = 0x02;

    /**
     * The relative destination address for ECU 4
     */
    public static final int DESTINATION_ECU_4              = 0x03;

    /**
     * The relative destination address for ECU 5
     */
    public static final int DESTINATION_ECU_5              = 0x04;

    /**
     * The relative destination address for ECU 6
     */
    public static final int DESTINATION_ECU_6              = 0x05;

    /**
     * The relative destination address for ECU 7
     */
    public static final int DESTINATION_ECU_7              = 0x06;

    /**
     * The functional address when using EFF addressing.
     */
    public static final int DESTINATION_EFF_FUNCTIONAL     = 0x33;

    /**
     * The address of the test equipment whenusing EFF addressing.
     */
    public static final int DESTINATION_EFF_TEST_EQUIPMENT = 0xF1;

    /**
     * The physical addressing type when using EFF addressing.
     */
    public static final int EFF_TYPE_PHYSICAL_ADDRESSING   = 0xDA;

    /**
     * The functional addressing type when using EFF addressing.
     */
    public static final int EFF_TYPE_FUNCTIONAL_ADDRESSING = 0xDB;

    /**
     * This mask can be used in a CAN filter to match functional response frames when using EFF addressing.
     */
    public static final int EFF_MASK_FUNCTIONAL_RESPONSE = 0xFFFF00FF;

    /**
     * This is the absolute base address for ECU requests when using SFF addressing.
     */
    public static final int SFF_ECU_REQUEST_BASE   = 0x7E0;

    /**
     * This is the absolute base address for ECU responses when using SFF addressing.
     */
    public static final int SFF_ECU_RESPONSE_BASE  = 0x7E8;

    /**
     * This is the absolute functional address when using SFF addressing.
     */
    public static final int SFF_FUNCTIONAL_ADDRESS = 0x7DF;

    /**
     * This mask can be used to match functional requests.
     */
    public static final int SFF_MASK_FUNCTIONAL_RESPONSE = 0b111_11111000;

    /**
     * This filter can be used to match functional requests.
     */
    public static final CanFilter SFF_FUNCTIONAL_FILTER = new CanFilter(SFF_ECU_RESPONSE_BASE, SFF_MASK_FUNCTIONAL_RESPONSE);

    /**
     * Constructs an EFF CAN ID given the ISOTP address components.
     *
     * @param priority the message priority
     * @param type the address type (functional vs physical)
     * @param sender the sender address
     * @param receiver the receiver address
     * @return the final CAN ID representing the ISOTP address
     */
    public static int effAddress(int priority, int type, int sender, int receiver) {
        return ((((priority & 0xFF) << 24) | ((type & 0xFF) << 16) | ((sender & 0xFF) << 8) | (receiver & 0xFF)) & CanId.EFF_MASK) | CanId.EFF_FLAG;
    }

    /**
     * Calculates the return address for the given address.
     *
     * @param addr the address to calculate the return address for
     * @return the return address
     */
    public static int returnAddress(int addr) {
        if (CanId.isExtended(addr)) {
            return effReturnAddress(addr);
        } else {
            return sffReturnAddress(addr);
        }
    }

    private static int effReturnAddress(int addr) {
        int[] components = decomposeEffAddress(addr);
        return effReturnAddress(components[0], components[1], components[2], components[3]);
    }

    private static int effReturnAddress(int priority, int type, int sender, int receiver) {
        return effAddress(priority, type, receiver, sender);
    }

    private static int sffReturnAddress(int addr) {
        int returnAddr;
        if ((addr & 0b1000) > 0) {
            returnAddr = addr - 8;
        } else {
            returnAddr = addr + 8;
        }
        return returnAddr;
    }

    private static boolean isEffAddressFunctional(int addr) {
        int[] components = decomposeEffAddress(addr);
        return isEffAddressFunctional(components[1], components[3]);
    }

    private static boolean isEffAddressFunctional(int type, int receiver) {
        return type == EFF_TYPE_FUNCTIONAL_ADDRESSING && receiver == DESTINATION_EFF_FUNCTIONAL;
    }

    private static boolean isSffAddressFunctional(int addr) {
        return addr == SFF_FUNCTIONAL_ADDRESS;
    }

    /**
     * Checks if the given address is addressing functionally.
     *
     * @param addr the address to check
     * @return true if the address is a functional address
     */
    public static boolean isFunctional(int addr) {
        if (CanId.isExtended(addr)) {
            return isEffAddressFunctional(addr);
        } else {
            return isSffAddressFunctional(addr);
        }
    }

    /**
     * Constructs a new {@link tel.schich.javacan.CanFilter} from the given destination address.
     *
     * @param addr the destination address
     * @return the new filter
     */
    public static CanFilter filterFromDestination(int addr) {
        if (CanId.isExtended(addr)) {
            int[] components = decomposeEffAddress(addr);
            int priority = components[0];
            int type = components[1];
            int sender = components[2];
            int receiver = components[3];

            if (isEffAddressFunctional(type, receiver)) {
                return new CanFilter(effAddress(priority, EFF_TYPE_PHYSICAL_ADDRESSING, 0x00, sender), EFF_MASK_FUNCTIONAL_RESPONSE);
            } else {
                return new CanFilter(effReturnAddress(priority, type, sender, receiver));
            }
        } else {
            if (isSffAddressFunctional(addr)) {
                return SFF_FUNCTIONAL_FILTER;
            } else {
                return new CanFilter(sffReturnAddress(addr));
            }
        }
    }

    /**
     * Decomposes the given EFF address into its components.
     *
     * @param effAddr the EFF address to decompose
     * @return an int array with 4 elements representing the components in order: priority, type, sender, receiver
     */
    public static int[] decomposeEffAddress(int effAddr) {
        int prio = (effAddr >>> 24);
        int type = ((effAddr >>> 16) & 0xFF);
        int from = ((effAddr >>> 8) & 0xFF);
        int to   = (effAddr & 0xFF);
        return new int[] {prio, type, from, to};
    }
}
