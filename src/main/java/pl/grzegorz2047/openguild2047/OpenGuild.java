/*
 * The MIT License
 *
 * Copyright 2014 Grzegorz.
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

/*
TODO:
Stworzenie klasy od cuboidów
Ogarnięcie co byłoby dobre do sprawdzania czy gracz jest na cuboidzie gildii
Tutaj są przydatne linki dla zainteresowanych tworzeniem cuboidów:
https://github.com/sk89q/worldguard/blob/master/src/main/java/com/sk89q/worldguard/bukkit/WorldGuardPlayerListener.java
https://forums.bukkit.org/threads/protection-region-cuboid-creation.164161/



*/

package pl.grzegorz2047.openguild2047;

import ca.wacos.nametagedit.NametagAPI;

import java.io.File;
import java.io.IOException;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import pl.grzegorz2047.openguild2047.api.Guilds;
import pl.grzegorz2047.openguild2047.commands.GildiaCommand;
import pl.grzegorz2047.openguild2047.handlers.MySQLHandler;
import pl.grzegorz2047.openguild2047.listeners.CuboidListeners;
import pl.grzegorz2047.openguild2047.listeners.EntityDamageByEntity;
import pl.grzegorz2047.openguild2047.listeners.Monitors;
import pl.grzegorz2047.openguild2047.listeners.PlayerChat;
import pl.grzegorz2047.openguild2047.listeners.PlayerMove;
import pl.grzegorz2047.openguild2047.listeners.PlayerQuit;

/**
 *
 * @author Grzegorz
 */
public class OpenGuild extends JavaPlugin {

    private static OpenGuild instance;
    private File log = new File("plugins/OpenGuild2047/logger/openguild.log");
    private File logDir = new File("plugins/OpenGuild2047/logger");
    private String address;
    private String database;
    private String login;
    private String password;

    @Override
    public void onEnable() {
    	long init = System.currentTimeMillis();
    	instance = this;
        if(!checkPlugins()){
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        copyDefaultFiles();
        loadAllListeners();
        Data pd = new Data();
        Data.setDataInstance(pd);
        loadDb();
        getCommand("gildia").setExecutor(new GildiaCommand());
        for(Player p : getServer().getOnlinePlayers()){
            if(Data.getInstance().isPlayerInGuild(p.getName())){
                NametagAPI.setPrefix(p.getName(), GenConf.colortagu + Data.getInstance().getPlayersGuild(p.getName()).getTag() +  "§r ");
            }
        }
        loadPlayers();
        CuboidListeners.loadItems();
        getServer().getConsoleSender().sendMessage("§a"+this.getName()+"§6 by §3grzegorz2047§6 zostal uruchomiony w " + String.valueOf(System.currentTimeMillis() - init) + " ms!"); //Oj krzaczy mi tu przez złe kodowanie Molek xd Ustaw sobie na UTF-8
        
    }

    @Override
    public void onDisable() {
        for(Player p : getServer().getOnlinePlayers()){
            if(NametagAPI.hasCustomNametag(p.getName())){
                NametagAPI.resetNametag(p.getName());
            }
        }
        
        // Usuwanie wszystkich plikow ktore nie posidaja formatu .log (logger tworzy duzo plikow roboczych)
        int logFiles = 0;
        for(File file : logDir.listFiles()) {
            String format = file.getName().substring(file.getName().length() - 4, file.getName().length());
            if(!format.equals(".log")) {
                file.delete();
                logFiles++;
            }
        }
        System.out.println("Usunieto " + logFiles + " plikow w folderze OpenGuild2047/log");
    }
    
    private boolean checkPlugins() {
    	if(getServer().getPluginManager().getPlugin("NametagEdit") == null) {
            Guilds.getLogger().severe("Nie znaleziono pluginu NametagEdit! Pobierz go ze strony http://dev.bukkit.org/bukkit-plugins/nametagedit/");
            Guilds.getLogger().severe("Wylaczanie pluginu OpenGuild2047...");
            return false;
    	}
        else{
            return true;
        }
    }
    
    private void copyDefaultFiles() {
    	saveDefaultConfig();//Najprostsza opcja, ale nie aktualizuje configu.
    	loadConfig();
    	if(!logDir.exists()) {
            logDir.mkdirs();
    	}
    	if(!log.exists()) {
            try {
                log.createNewFile();
            } catch(IOException ex) {
                ex.printStackTrace();
            }
    	}
        saveResource("messages.yml", false);
        saveResource("players.yml", false);
    }
    
    private void loadConfig() {
        // MySQL
        address = getConfig().getString("mysql.adres");
        database = getConfig().getString("mysql.baza-danych");
        login = getConfig().getString("mysql.login");
        password = getConfig().getString("mysql.haslo");
        // Inne ustawienia
        GenConf.teampvp = this.getConfig().getBoolean("teampvp");
        GenConf.homecommand = this.getConfig().getBoolean("dom-command");
        GenConf.reqitems = this.getConfig().getStringList("WymaganePrzedmioty");
        GenConf.playerprefixenabled = this.getConfig().getBoolean("playerprefixtag");
        GenConf.guildprefixinchat = this.getConfig().getBoolean("guildprefixinchat");
        GenConf.colortagu = this.getConfig().getString("kolortagugildii").replace('&', '§');
        if(GenConf.colortagu.length() > 2){
            Guilds.getLogger().severe("Kolor tagu moze skladac sie tylko z 2 znaków: & oraz znaku identyfikujacy kolor!");
            Guilds.getLogger().warning("Uzywam domyslnego koloru gildii!");
            GenConf.colortagu = "§6";
        }
    }
    
    private void loadDb() {
        switch(GenConf.DATABASE) {
        case FILE:
            Guilds.getLogger().warning("Przykro nam, ale system plików jeszcze nie dziala! Próba polaczenia sie z baza danych MySQL...");
            new MySQLHandler(address, database, login, password); // TODO Pliki...
            break;
        case MYSQL:
            new MySQLHandler(address, database, login, password);
            break;
        default:
            Guilds.getLogger().severe("Nie mozna wczytac typu bazy danych (database)! Popraw to w pliku config.yml!");
            getServer().getPluginManager().disablePlugin(this);
            break;
        }
    }
    
    void loadAllListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new CuboidListeners(), this);
        pm.registerEvents(new PlayerChat(), this);
        pm.registerEvents(new PlayerMove(), this);
        pm.registerEvents(new PlayerQuit(), this);
        pm.registerEvents(new Monitors(), this);
        pm.registerEvents(new EntityDamageByEntity(), this);

    }
    
    private void loadPlayers() {
        for(SimpleGuild guild : Data.getInstance().guilds.values()) { // Pobieranie gildii
            for(String member : MySQLHandler.getGuildMembers(guild.getTag())) { // Pobieranie graczy w gildii
                guild.addMember(member); // Dodawanie gracza do listy
            }
        }
    }
    
    public static OpenGuild get() {
    	return instance;
    }
    

    
}
