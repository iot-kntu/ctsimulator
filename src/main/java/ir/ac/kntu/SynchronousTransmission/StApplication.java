package ir.ac.kntu.SynchronousTransmission;

import ir.ac.kntu.SynchronousTransmission.events.StFloodPacket;

import java.util.Objects;

public interface StApplication {

    default void onTimeProgress(ReadOnlyContext context){}

    default StMessage<?> onInitiateFlood(ReadOnlyContext context){
        Objects.requireNonNull(context);
        return new StMessage<>(context.getRoundInitiator(), "");
    }

    default void onPacketReceive(StFloodPacket<?> packet, ReadOnlyContext context){

    }

}

