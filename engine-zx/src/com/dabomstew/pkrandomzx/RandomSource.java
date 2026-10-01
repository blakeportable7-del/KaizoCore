package com.dabomstew.pkrandomzx;

/*----------------------------------------------------------------------------*/
/*--  RandomSource.java - functions as a centralized source of randomness   --*/
/*--                      to allow the same seed to produce the same random --*/
/*--                      ROM consistently.                                 --*/
/*--                                                                        --*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
/*--  Originally part of "Universal Pokemon Randomizer" by Dabomstew        --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2020.                                 --*/
/*--                                                                        --*/
/*--  The custom code written here is licensed under the terms of the GPL:  --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import java.security.SecureRandom;
import java.util.Random;

public class RandomSource {

    // LOCAL MODIFICATION (KaizoCore): a randomize on an interrupted thread
    // stops at its next draw. The app makes the next run ahead on a
    // background thread (NextRunJob) and has to stop it when the player
    // starts a different run; nothing else can stop the engine midway. The
    // numbers are java.util.Random's own for the same seed, so a seed still
    // makes the same ROM.
    private static final class Stoppable extends Random {
        private static final long serialVersionUID = 1L;

        @Override
        protected int next(int bits) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException("randomize stopped");
            }
            return super.next(bits);
        }
    }

    private static Random source = new Stoppable();
    private static Random cosmeticSource = new Stoppable();
    private static int calls = 0;
    private static int cosmeticCalls = 0;
    private static Random instance = new RandomSourceInstance();
    private static Random cosmeticInstance = new CosmeticRandomSourceInstance();

    public static void reset() {
        source = new Stoppable();
        cosmeticSource = new Stoppable();
        calls = 0;
        cosmeticCalls = 0;
    }

    public static void seed(long seed) {
        source.setSeed(seed);
        cosmeticSource.setSeed(seed);
        calls = 0;
        cosmeticCalls = 0;
    }

    public static double random() {
        calls++;
        return source.nextDouble();
    }

    public static int nextInt(int size) {
        calls++;
        return source.nextInt(size);
    }

    public static int nextIntCosmetic(int size) {
        cosmeticCalls++;
        return cosmeticSource.nextInt(size);
    }

    public static void nextBytes(byte[] bytes) {
        calls++;
        source.nextBytes(bytes);
    }

    public static int nextInt() {
        calls++;
        return source.nextInt();
    }

    public static long nextLong() {
        calls++;
        return source.nextLong();
    }

    public static boolean nextBoolean() {
        calls++;
        return source.nextBoolean();
    }

    public static float nextFloat() {
        calls++;
        return source.nextFloat();
    }

    public static double nextDouble() {
        calls++;
        return source.nextDouble();
    }

    public static synchronized double nextGaussian() {
        calls++;
        return source.nextGaussian();
    }

    public static long pickSeed() {
        long value = 0;
        byte[] by = SecureRandom.getSeed(6);
        for (int i = 0; i < by.length; i++) {
            value |= ((long) by[i] & 0xffL) << (8 * i);
        }
        return value;
    }

    public static Random instance() {
        return instance;
    }

    public static Random cosmeticInstance() {
        return cosmeticInstance;
    }

    public static int callsSinceSeed() {
        return calls + cosmeticCalls;
    }

    private static class RandomSourceInstance extends Random {

        /**
         * 
         */
        private static final long serialVersionUID = -4876737183441746322L;

        @Override
        public synchronized void setSeed(long seed) {
            RandomSource.seed(seed);
        }

        @Override
        public void nextBytes(byte[] bytes) {
            RandomSource.nextBytes(bytes);
        }

        @Override
        public int nextInt() {
            return RandomSource.nextInt();
        }

        @Override
        public int nextInt(int n) {
            return RandomSource.nextInt(n);
        }

        @Override
        public long nextLong() {
            return RandomSource.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            return RandomSource.nextBoolean();
        }

        @Override
        public float nextFloat() {
            return RandomSource.nextFloat();
        }

        @Override
        public double nextDouble() {
            return RandomSource.nextDouble();
        }

        @Override
        public synchronized double nextGaussian() {
            return RandomSource.nextGaussian();
        }

    }

    private static class CosmeticRandomSourceInstance extends Random {

        @Override
        public synchronized void setSeed(long seed) {
            RandomSource.seed(seed);
        }

        @Override
        @Deprecated
        public void nextBytes(byte[] bytes) {

        }

        @Override
        @Deprecated
        public int nextInt() {
            return 0;
        }

        @Override
        public int nextInt(int n) {
            return RandomSource.nextIntCosmetic(n);
        }

        @Override
        @Deprecated
        public long nextLong() {
            return 0;
        }

        @Override
        @Deprecated
        public boolean nextBoolean() {
            return false;
        }

        @Override
        @Deprecated
        public float nextFloat() {
            return 0;
        }

        @Override
        @Deprecated
        public double nextDouble() {
            return 0;
        }

        @Override
        @Deprecated
        public synchronized double nextGaussian() {
            return 0;
        }
    }
}
