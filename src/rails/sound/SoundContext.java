/**
 * 
 */
package rails.sound;

import rails.game.*;

/**
 * Takes care of the current context from a music/sfx perspective.
 * Reacts on context changes by triggering changes to the played music/sfx. 
 * 
 * @author Frederick Weld
 *
 */
public class SoundContext {
    private double averageRevenue = 50;
    //to which degree (from 0=none to 1=full) is new revenue considered for determining 
    //the new average revenue value 
    private final static double slidingAverageAdjustmentFactor = 0.2;
    //how much percent of the set revenue sfx is played if the revenue is average
    private final static double averageSetRevenuePlaySoundProportion = 0.4;
    //how much percent of the set revenue sfx is played if the revenue is epsilon;
    private final static double minimumSetRevenuePlaySoundProportion = 0.167;
    
    private Round currentRound = null;
    private Phase currentPhase = null;
    private String currentBackgroundMusicFileName = null;
    
    private SoundPlayer player;
    
    public SoundContext(SoundPlayer player) {
        this.player = player;
    }
    
    public void notifyOfMusicEnablement(boolean musicEnabled) {
        if (!musicEnabled && player.isBGMPlaying()) {
            player.stopBGM();
        }
        if (musicEnabled && !player.isBGMPlaying()) {
            String musicFileNamePriorToDisable = currentBackgroundMusicFileName;
            
            //try to start BGM based on rounds / phases
            currentBackgroundMusicFileName = null;
            playBackgroundMusic();
            
            //if no BGM could be started, replay the music that was active before disabling
            //BGM music
            if (currentBackgroundMusicFileName == null) {
                currentBackgroundMusicFileName = musicFileNamePriorToDisable;
                player.playBGM(musicFileNamePriorToDisable);
            }
        }
    }

    public void notifyOfSetRevenue(int actualRevenue) {
        //ignore zero revenue
        if (actualRevenue <= 0) return;
        
        double playSoundProportion = minimumSetRevenuePlaySoundProportion
                + ( 1 - minimumSetRevenuePlaySoundProportion )
                * ( averageSetRevenuePlaySoundProportion - minimumSetRevenuePlaySoundProportion )
                * actualRevenue / averageRevenue;
        if (playSoundProportion > 1) playSoundProportion = 1;

        player.playSFXByConfigKey (SoundConfig.KEY_SFX_OR_SetRevenue,
                playSoundProportion);

        averageRevenue = actualRevenue * slidingAverageAdjustmentFactor
                + averageRevenue * (1 - slidingAverageAdjustmentFactor);
    }

    /**
     * @return true if new background music is played
     */
    private boolean playBackgroundMusic() {
        boolean isNewMusicPlayed = false;
        
        //do nothing if music is not enabled
        if (!SoundConfig.isBGMEnabled()) return false;
        
        String currentRoundConfigKey = null;
        if (currentRound instanceof StartRound) {
            currentRoundConfigKey = SoundConfig.KEY_BGM_StartRound;
        } else if (currentRound instanceof StockRound) {
            currentRoundConfigKey = SoundConfig.KEY_BGM_StockRound;
        } else if (currentRound instanceof OperatingRound) {
            currentRoundConfigKey = SoundConfig.KEY_BGM_OperatingRound;
        } else if (currentRound instanceof EndOfGameRound) {
            currentRoundConfigKey = SoundConfig.KEY_BGM_EndOfGameRound;
        }
        //only play anything if round is recognized and new music is to be played
        if (currentRoundConfigKey != null) {
            String currentPhaseName = "";
            if (currentPhase != null) currentPhaseName = currentPhase.getName();
            String newBackgroundMusicFileName = SoundConfig.get(
                    currentRoundConfigKey, currentPhaseName);
            if (!newBackgroundMusicFileName.equals(currentBackgroundMusicFileName)) {
                currentBackgroundMusicFileName = newBackgroundMusicFileName;
                isNewMusicPlayed = true;
                
                //additionally play stock market opening bell if appropriate
                if (SoundConfig.isSFXEnabled() && currentRound instanceof StockRound) {
                    player.playSFXWithFollowupBGM(
                            SoundConfig.get(SoundConfig.KEY_SFX_SR_OpeningBell),
                            newBackgroundMusicFileName);
                } else {
                    player.playBGM(newBackgroundMusicFileName);
                }
            }
        }
        return isNewMusicPlayed;
    }
    public void notifyOfPhase(Phase newPhase) {
        if (!newPhase.equals(currentPhase)) {
            currentPhase = newPhase;
            playBackgroundMusic();
        }
    }

    public void notifyOfRound(Round newRound) {
        if (!newRound.equals(currentRound)) {
            currentRound = newRound;
            boolean isNewMusicPlayed = playBackgroundMusic();
            
            //play stock market opening bell for stock rounds without new background music
            //(e.g., if music is disabled but sfx is enabled)
            if (!isNewMusicPlayed && SoundConfig.isSFXEnabled() 
                    && currentRound instanceof StockRound) {
                player.playSFXByConfigKey(SoundConfig.KEY_SFX_SR_OpeningBell);
            }
        }
    }
    public void notifyOfGameSetup() {
        currentBackgroundMusicFileName = SoundConfig.get(SoundConfig.KEY_BGM_GameSetup);
        if (SoundConfig.isBGMEnabled()) player.playBGM(currentBackgroundMusicFileName);
    }
}
