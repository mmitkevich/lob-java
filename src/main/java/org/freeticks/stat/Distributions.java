package org.freeticks.stat;

import java.util.Random;

public class Distributions {
    public Random random;

    public Distributions(Random random) {
        this.random = random;
    }

    public int poisson(double lambda) {
        double L = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            p = p * random.nextDouble();
            k++;
        } while (p > L);
        return k - 1;
    }

    public double normal(double mu, double sigma){
        return sigma*random.nextGaussian() + mu;
    }

    public double gaussian() {
        return random.nextGaussian();
    }

    public double uniform() {
        return random.nextDouble();
    }
}
