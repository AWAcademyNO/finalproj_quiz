package com.example.finalproj_quiz;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;


@Controller
public class QuestionsController {

    @Autowired
    ObjectMapper mapper;

    // int variables
    private int playerCounter;
    private int questionNumber;
    private int numberOfQuestions;
    private int quizCode;

    // boolean variables
    private boolean isReady = false;
    private boolean isFinalQuestion = false;

    // object variables
    private Player player;

    // list, array, hashmap variables
    private Questions[] questions;
    private List<Player> listOfPlayers = new ArrayList<>();
    private HashMap<String, Integer> scoreboard = new HashMap<>();


    //------------------------------------------------------------------------------------------------------------------


    // front page
    @GetMapping("/")
    public String frontPage(){
        return "front_page";
    }


    //----------------------------------------------- admin only -------------------------------------------------------


    // admin only : initializes and clears anything needed for the quiz
    @GetMapping("/register-quiz")
    public String initializeQuiz(Model model) {
        model.addAttribute("categoriesList", categoriesList());

        scoreboard.clear();
        listOfPlayers.clear();
        isReady = false;
        isFinalQuestion = false;
        questionNumber = 0;
        quizCode = generateRandomQuizCode();
        return "admin_first_page";
    }

    // admin only : creates quiz and sets role "admin" to the player object in session
    @PostMapping("/register-quiz")
    public String registerQuiz(@RequestParam Integer inputNumberOfQuestions, HttpSession session, @RequestParam(required = false) List<String> category){
        numberOfQuestions = inputNumberOfQuestions;
        if(category != null){
            questions = getQuizWithCategory(category, inputNumberOfQuestions);
        }else{
            questions = getQuiz(inputNumberOfQuestions);
        }

        player = new Player();
        player.setRole("admin");
        session.setAttribute("player", player);
        return "redirect:/play/" + quizCode;
    }


    //----------------------------------------------- player only ------------------------------------------------------


    // player only : form to register players
    @GetMapping("/register-players")
    public String registerPlayers(){
        return "register_players";
    }

    // player only : registered players through form and sets role "player" to the player object in session
    @PostMapping("/register-players")
    public String registeredPlayers(@RequestParam String userQuizCode, @RequestParam String name, HttpSession session){
        player = new Player(name);
        player.setRole("player");
        listOfPlayers.add(player);
        session.setAttribute("player", player);

        scoreboard.put(player.getName(), 0);
        return "redirect:/play/" + userQuizCode;
    }


    //------------------------------------------------------------------------------------------------------------------


    // Start quiz from the generated quiz
    @GetMapping("/play/{quizCode}")
    public String startQuiz(@PathVariable String quizCode, Model model, HttpSession session){
        model.addAttribute("listOfPlayers", listOfPlayers);
        model.addAttribute("quizCode", quizCode);
        model.addAttribute("questionNumber", questionNumber);
        model.addAttribute("isReady", isReady);
        model.addAttribute("player", session.getAttribute("player"));

        // controls flow of players
        if (isReady){
            playerCounter++;
            if(playerCounter == listOfPlayers.size()) {
                isReady = false;
            }
            return "redirect:/play/" + quizCode + '/' + questionNumber;
        }
        return "start_quiz";
    }

    // post method when admin has clicked on start quiz
    @PostMapping("/play/{quizCode}")
    public String postStartQuiz(@PathVariable String quizCode){
        isReady = true;
        playerCounter = 0;
        return "redirect:/play/" + quizCode + '/' + questionNumber;
    }


    // display page for each quiz question
    @GetMapping("/play/{quizCode}/{questionNumber}")
    public String questionPage(@PathVariable int quizCode, @PathVariable int questionNumber, Model model, HttpSession session) throws JsonProcessingException {
        if (questionNumber == numberOfQuestions-1) {
            isFinalQuestion = true;
        }

        model.addAttribute("question", mapper.writeValueAsString(questions[questionNumber].getQuestion()));
        model.addAttribute("player", session.getAttribute("player"));

        List<String> alternatives = Arrays.asList("A", "B", "C", "D");
        Collections.shuffle(alternatives);

        model.addAttribute(alternatives.get(0), mapper.writeValueAsString(questions[questionNumber].getCorrectAnswer()).replaceAll("^\"|\"$", ""));
        model.addAttribute("correctAnswer", alternatives.get(0));

        model.addAttribute(alternatives.get(1), mapper.writeValueAsString(questions[questionNumber].getIncorrectAnswers()[0]).replaceAll("^\"|\"$", ""));
        model.addAttribute(alternatives.get(2), mapper.writeValueAsString(questions[questionNumber].getIncorrectAnswers()[1]).replaceAll("^\"|\"$", ""));
        model.addAttribute(alternatives.get(3), mapper.writeValueAsString(questions[questionNumber].getIncorrectAnswers()[2]).replaceAll("^\"|\"$", ""));

        model.addAttribute("selectedAnswer", "none");


        return "question_page";
    }

    // retrieves answers from players
    @PostMapping("/play/{quizCode}/{questionNumber}")
    public String postScore(@PathVariable int quizCode, @PathVariable int questionNumber, HttpSession session, Model model, @RequestParam(required = false) String answer) throws JsonProcessingException {
        player = (Player) session.getAttribute("player");
        model.addAttribute("player", player);


        if (player.getRole().equals("player") && mapper.writeValueAsString(questions[questionNumber].getCorrectAnswer()).replaceAll("^\"|\"$", "").equals(answer)){
                int tempScore = scoreboard.get(player.getName());
                scoreboard.put(player.getName(), tempScore+1);
        }

        // returns result page is this is currently the last question
        if (isFinalQuestion){
            resetQuestionNumber();
            model.addAttribute("scoreboard", scoreboard);
            return "result_page";
        }

        player = (Player) session.getAttribute("player");
        if(player.getRole().equals("admin")) {
            nextQuestion();
        }

        return "redirect:/play/" + quizCode + "/wait";
    }



    //---------------------------------------------- waiting page ------------------------------------------------------

    @GetMapping("/play/{quizCode}/wait")
    public String waitingPage(@PathVariable int quizCode, Model model, HttpSession session){

        if (isReady){
            playerCounter++;
            if(playerCounter == listOfPlayers.size()) {
                isReady = false;
            }
            return "redirect:/play/" + quizCode + '/' + questionNumber;
        }

        model.addAttribute("scoreboard", scoreboard);
        model.addAttribute("player", session.getAttribute("player"));

        return "waiting_page";
    }




    //----------------------------------------------- functions --------------------------------------------------------


    // retrieves quiz as array from api with 'number' amount of questions and returns value
    public Questions[] getQuiz(int number){
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Questions[]> quizzes = restTemplate.getForEntity("https://api.trivia.willfry.co.uk/questions?limit=" + number, Questions[].class);
        return quizzes.getBody();
    }

    //
    public Questions[] getQuizWithCategory(List<String> categories, int number){
        RestTemplate restTemplate = new RestTemplate();
        List<String> newCategories = new ArrayList<>();

        for (String category : categories) {
            newCategories.add(category.replaceAll(" ", "_"));
        }

        String categoriesAsString = newCategories.stream()
                .map(n -> String.valueOf(n))
                .collect(Collectors.joining(",")).toLowerCase();
        ResponseEntity<Questions[]> quizzes = restTemplate.getForEntity("https://api.trivia.willfry.co.uk/questions?categories=" + categoriesAsString + "&limit=" + number, Questions[].class);

        return quizzes.getBody();
    }

    // function to increase question number
    public void nextQuestion(){
        questionNumber++;
    }

    // function to reset question number
    public void resetQuestionNumber(){
        questionNumber = 0;
    }

    // function to generate a random number between 1 and 1000 that represents the quiz code
    public int generateRandomQuizCode(){
        return ThreadLocalRandom.current().nextInt(1, 1000);
    }

    public List<String> categoriesList(){
        List<String> categoriesList = new ArrayList<>();
        categoriesList.add("Food and Drink");
        categoriesList.add("Geography");
        categoriesList.add("General Knowledge");
        categoriesList.add("History");
        categoriesList.add("Art and Literature");
        categoriesList.add("Movies");
        categoriesList.add("Music");
        categoriesList.add("Science");
        categoriesList.add("Society and Culture");
        categoriesList.add("Sport and Leisure");

        return categoriesList;

    }




}





