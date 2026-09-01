package dev.sweety.saas.service;

import dev.sweety.netty.common.backend.IBackend;

public interface IService extends IBackend {

    ServiceType type();

    @Override
    default int typeId(){
        return type().id();
    }

}
