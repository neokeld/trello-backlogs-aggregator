package io.tools.trellobacklogsaggregator.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.julienvey.trello.domain.Argument;
import com.julienvey.trello.domain.Board;
import com.julienvey.trello.domain.Label;
import com.julienvey.trello.domain.Member;
import com.julienvey.trello.domain.TList;

import io.tools.trellobacklogsaggregator.configuration.CustomConfiguration;
import io.tools.trellobacklogsaggregator.model.BacklogError;
import io.tools.trellobacklogsaggregator.model.BacklogsData;
import io.tools.trellobacklogsaggregator.model.BoardDetail;
import io.tools.trellobacklogsaggregator.model.Sprint;

@Service
public class TrelloService {

    @Autowired
    private TrelloApi trelloApi;

    @Autowired
    private BoardService boardService;

    @Autowired
    private ListService listService;

    @Autowired
    private SprintService sprintService;

    @Autowired
    private CustomConfiguration customConfiguration;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private BoardDetail detailedBoard;
    private Sprint sprint;

    public BacklogsData readBacklogs(String organizationId) {
        List<Board> storiesBoards = getBoards(organizationId);
        List<BacklogError> errors = new ArrayList<>();
        Map<String, Member> members = getMembers(organizationId);

        List<BoardDetail> storiesDetailedBoards = new ArrayList<>();
        sprint = new Sprint();
        int i = 0;
        for (Board board : storiesBoards) {
            List<TList> tLists = board.fetchLists();
            detailedBoard = new BoardDetail(board);

            try {
                listService.checkListConsistency(tLists);
            } catch (Exception e) {
                errors.add(new BacklogError(board.getName(), e.getMessage()));
            }
            tLists.forEach(tList -> {
                trelloApi.getListCards(tList.getId()).forEach(card -> {
                    detailedBoard = boardService.addCard(detailedBoard, card);
                    if (listService.checkListInSprint(tList)) {
                        sprint = sprintService.addCard(sprint, tList, card, members);
                    }
                });
            });

            storiesDetailedBoards.add(detailedBoard);
            logger.debug(++i + "/" + storiesBoards.size() + " (" + board.getName() + " OK)");
        }

        BacklogsData backlogsData = new BacklogsData();
        backlogsData.setBoards(storiesDetailedBoards);
        backlogsData.setSprint(sprint);
        backlogsData.setErrors(errors);
        return backlogsData;
    }

    private Map<String, Member> getMembers(String organizationId) {
        List<Member> membersList = trelloApi.getOrganizationMembers(organizationId);
        Map<String, Member> members = new HashMap<>();
        membersList.forEach(member -> {
            members.put(member.getId(), member);
        });
        return members;
    }

    private List<Board> getBoards(String organizationId) {
        Argument fields = new Argument("fields", "name");
        List<Board> boards = trelloApi.getOrganizationBoards(organizationId, fields);
        List<Board> storiesBoards = new ArrayList<>();
        boards.stream().filter(board -> board.getName().matches(customConfiguration.getBoardsPattern()))
                .forEach(board -> {
                    storiesBoards.add(board);
                });
        return storiesBoards;
    }
}
