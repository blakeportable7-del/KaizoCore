package com.dabomstew.pkrandom122.pokemon;

public class PickupItem {
    public int item;
    public int[] probabilities;

    public PickupItem(int item) {
        this.item = item;
        this.probabilities = new int[10];
    }
}
