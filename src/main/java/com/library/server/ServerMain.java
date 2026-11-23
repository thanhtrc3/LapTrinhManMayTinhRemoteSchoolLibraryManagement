package com.library.server;

import com.library.server.network.LibraryServer;
import com.library.server.service.LibraryService;
import com.library.server.storage.DataStorage;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8888;
        String dataDir = System.getProperty("library.data", "data");
        LibraryService service = new LibraryService(new DataStorage(dataDir));
        new LibraryServer(service, port).start();
    }
}
