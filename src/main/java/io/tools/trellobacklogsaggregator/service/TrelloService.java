package io.tools.trellobacklogsaggregator.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.julienvey.trello.domain.Argument;
import com.julienvey.trello.domain.Board;
import com.julienvey.trello.domain.TList;

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
    private StoryBoardService storyBoardService;

    @Autowired
    private SprintService sprintService;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private BoardDetail detailedBoard;
    private Sprint sprint;

    public BacklogsData readBacklogs(String organizationId) {
        List<Board> storiesBoards = getBoards(organizationId);

        List<BoardDetail> storiesDetailedBoards = new ArrayList<>();
        sprint = new Sprint();
        for (Board board : storiesBoards) {
            List<TList> tLists = board.fetchLists();
            detailedBoard = new BoardDetail(board);

            boolean consistency = storyBoardService.checkListConsistency(tLists);
            if (!consistency) {
                logger.error(board.getName() + " ne contient pas toutes les colonnes définies dans le modèle de backlog");
            }
            tLists.forEach(tList -> {
                trelloApi.getListCards(tList.getId()).forEach(card -> {
                    detailedBoard = boardService.addCard(detailedBoard, card);
                    if (storyBoardService.checkListInSprint(tList)) {
                        sprint = sprintService.addCard(sprint, tList, card);
                    }
                });
            });

            storiesDetailedBoards.add(detailedBoard);
        }

        BacklogsData backlogsData = new BacklogsData();
        backlogsData.setBoards(storiesDetailedBoards);
        backlogsData.setSprint(sprint);
        return backlogsData;
    }

    private List<Board> getBoards(String organizationId) {
        Argument fields = new Argument("fields", "name");
        List<Board> boards = trelloApi.getOrganizationBoards(organizationId, fields);
        List<Board> storiesBoards = new ArrayList<>();
        boards.stream().filter(board -> board.getName().startsWith("Backlog")).forEach(board -> {
            storiesBoards.add(board);
            logger.debug(board.getName());
        });
        return storiesBoards;
    }
}