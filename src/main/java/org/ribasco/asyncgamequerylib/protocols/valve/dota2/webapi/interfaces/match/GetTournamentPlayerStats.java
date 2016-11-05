package org.ribasco.asyncgamequerylib.protocols.valve.dota2.webapi.interfaces.match;

import org.ribasco.asyncgamequerylib.protocols.valve.dota2.webapi.Dota2ApiConstants;
import org.ribasco.asyncgamequerylib.protocols.valve.dota2.webapi.requests.Dota2MatchRequest;

public class GetTournamentPlayerStats extends Dota2MatchRequest {
    public GetTournamentPlayerStats(int apiVersion, long accountId, int leagueId, int heroId, String timeFrame, long matchId, int phaseId) {
        super(Dota2ApiConstants.DOTA2_METHOD_GETTOURNPLAYERSTATS, apiVersion, null);
        urlParam("account_id", accountId);
        urlParam("league_id", leagueId);
        urlParam("hero_id", heroId);
        urlParam("time_frame", timeFrame);
        urlParam("match_id", matchId);
        urlParam("phase_id", phaseId);
    }
}