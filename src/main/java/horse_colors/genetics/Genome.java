package sekelsta.horse_colors.genetics;

import sekelsta.horse_colors.config.HorseConfig;
import sekelsta.horse_colors.renderer.ComplexLayeredTexture;
import sekelsta.horse_colors.renderer.TextureLayer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.ArrayList;

public abstract class Genome {
    public abstract List<String> listGenes();
    public abstract List<String> listGenericChromosomes();
    public abstract List<String> listStats();

    protected IGeneticEntity entity;

    protected String textureCacheName;
    protected ArrayList<TextureLayer> textureLayers;

    // Make sure to use this.entity.getRand() instead for anything
    // that should be consistent across worlds with the same seed
    public static java.util.Random rand = new java.util.Random();

    public Genome(IGeneticEntity entityIn) {
        this.entity = entityIn;
    }

    public void resetTexture() {
        this.textureCacheName = null;
    }

    public abstract List<String> humanReadableNamedGenes(boolean showAll);
    //public abstract List<String> humanReadableStats(boolean showAll);
    public abstract void setTexturePaths();
    public abstract String genesToString();
    public abstract void genesFromString(String s);
    public abstract boolean isValidGeneString(String s);

    @OnlyIn(Dist.CLIENT)
    public String getTexture()
    {
        if (this.textureCacheName == null)
        {
            this.setTexturePaths();
        }
        return this.textureCacheName;
    }

    @OnlyIn(Dist.CLIENT)
    public ArrayList<TextureLayer> getVariantTexturePaths()
    {
        if (this.textureCacheName == null)
        {
            this.setTexturePaths();
        }

        return this.textureLayers;
    }

    public abstract int getGeneSize(String gene);

    public int getChromosome(String name) {
        return entity.getChromosome(name);
    }

    public void setNamedGene(String name, int val)
    {
        String chr = getGeneChromosome(name);
        entity.setChromosome(chr, (entity.getChromosome(chr) & (~getGeneLoci(name))) 
            | (val << (getGenePos(name) % 32)));
    }

    public int getNamedGene(String name)
    {
        String chr = getGeneChromosome(name);
        // Use unsigned right shift to avoid returning negative numbers
        return (entity.getChromosome(chr) & getGeneLoci(name)) >>> getGenePos(name);
    }

    // This returns the chromosome masked to contain only the relevent bits
    public int getRawStat(String name)
    {
        String chr = listGenericChromosomes().get(getStatPos(name) / 32);
        return entity.getChromosome(chr) & getStatLoci(name);
    }

    // This returns the number of '1' bits in the stat's position
    public int getStatValue(String name)
    {
        int val = getRawStat(name);
        return countBits(val);
    }

    public int countBits(int val) {
        int count = 0;
        for (int i = 0; i < 32; ++i)
        {
            count += ((val % 2) + 2) % 2;
            val >>= 1;
        }
        return count;
    }

    public int getGenePos(String name)
    {
        return getPos(name, listGenes());
    }

    public int getStatPos(String name)
    {
        return getPos(name, listStats());
    }

    private int getPos(String name, List<String> genes)
    {
        int i = 0;
        for (String gene : genes)
        {
            int next = (i + (2 * getGeneSize(gene)));
            // Special case to keep each gene completely on the same int
            if (next / 32 != i / 32 && next % 32 != 0)
            {
                i = (i / 32 + 1) * 32;
            }

            if (gene == name)
            {
                return i;
            }
            i += (2 * getGeneSize(gene));
        }

        // Return statement needed to compile
        System.out.println("Gene not recognized: " + name);
        return -1;
    }

    public int getGeneLoci(String gene)
    {
        return getLoci(gene, getGenePos(gene));
    }

    public int getStatLoci(String gene)
    {
        return getLoci(gene, getStatPos(gene));
    }

    /* This returns a bitmask which is 1 where the gene is stored and 0 everywhere else. */
    private int getLoci(String gene, int pos)
    {
        return ((1 << (2 * getGeneSize(gene))) - 1) << (pos % 32);
    }

    public String getGeneChromosome(String gene)
    {
        // Which of the ints full of genes ours is on
        return Integer.toString(getGenePos(gene) / 32);
    }

    public int getAllele(String name, int n)
    {
        int gene = getNamedGene(name);
        gene >>= n * getGeneSize(name);
        gene %= 1 << getGeneSize(name);
        return gene;
    }

    public void setAllele(String name, int n, int v)
    {
        int other = getAllele(name, 1 - n);
        int size = getGeneSize(name);
        setNamedGene(name, (other << ((1 - n) * size)) | (v << (n * size)));
    }

    // Replace the given allele with a random one.
    // It may be the same as before.
    public void mutateAllele(String gene, int n) {
        int size = getGeneSize(gene);
        int v = this.rand.nextInt((int)Math.pow(2, size));
        setAllele(gene, n, v);
    }

    // Will mutate with p probability
    public void mutateAlleleChance(String gene, int n, double p) {
        if (this.rand.nextDouble() < p) {
            mutateAllele(gene, n);
        }
    }

    // Get a number where each binary digit has p
    // probability of being a 1. 
    public int mutateIntMask(double p) {
        int mask = 0;
        if (this.rand.nextDouble() < p) {
            mask++;
        }
        for (int i = 1; i < Integer.SIZE; ++i) {
            mask <<= 1;
            if (this.rand.nextDouble() < p) {
                mask++;
            }
        }
        return mask;
    }


    public void mutateGenericChromosome(String name, double p) {
        // xor with an int where each digit has a p / 2 chance of being 1
        // This is equivalent to picking a random replacement with p probability
        // because half the time that would pick the same value as before
        entity.setChromosome(name, entity.getChromosome(name) ^ mutateIntMask(p / 2));
    }

    public void mutate() {
        double p = HorseConfig.Common.mutationChance.get();
        for (String gene : listGenes()) {
            mutateAlleleChance(gene, 0, p);
            mutateAlleleChance(gene, 1, p);
        }
        for (String stat : listGenericChromosomes()) {
            mutateGenericChromosome(stat, p);
        }
    }


    public boolean hasAllele(String name, int allele)
    {
        return getAllele(name, 0) == allele || getAllele(name, 1) == allele;
    }

    public int getMaxAllele(String name)
    {
        return Math.max(getAllele(name, 0), getAllele(name, 1));
    }

    public boolean isHomozygous(String name, int allele)
    {
        return  getAllele(name, 0) == allele && getAllele(name, 1) == allele;
    }

    public int countAlleles(String gene, int allele) {
        int count = 0;
        if (getAllele(gene, 0) == allele) {
            count++;
        }
        if (getAllele(gene, 1) == allele) {
            count++;
        }
        return count;
    }

    public int getRandomGenericGenes(int n, int data)
    {
        int rand = this.rand.nextInt();
        int answer = 0;
        for (int i = 0; i < 16; i++)
        {
            if (rand % 2 == 0)
            {
                answer += (data & (1 << (2 * i))) << n;
            }
            else 
            {
                answer += (data & (1 << (2 * i + 1))) >> 1 - n;
            }
            rand >>= 1;
        }
        return answer;
    }

    // 
    public void inheritNamedGenes(Genome parent1, Genome parent2) {
        for (String gene : this.listGenes()) {
            int allele1 = parent1.getAllele(gene, this.rand.nextInt(2));
            int allele2 = parent2.getAllele(gene, this.rand.nextInt(2));
            this.setAllele(gene, 0, allele1);
            this.setAllele(gene, 1, allele2);
        }
    }

    public void inheritGenericGenes(Genome parent1, Genome parent2) {
        for (String chr : this.listGenericChromosomes()) {
            int mother = parent1.getRandomGenericGenes(1, parent1.getChromosome(chr));
            int father = parent2.getRandomGenericGenes(0, parent2.getChromosome(chr));
            this.entity.setChromosome(chr, mother | father);
        }
    }

    public void inheritGenes(Genome parent1, Genome parent2) {
        inheritNamedGenes(parent1, parent2);
        inheritGenericGenes(parent1, parent2);
        mutate();
    }
}
