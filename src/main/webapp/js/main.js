let ws;
let inRoom = false;
let currentRoomCode = null;
let userNamed = false;
let gameOngoing = false;

let thisUsername = "";
let playerOne = "";
let playerTwo = "";
let playerTurn = "";

//function called when Create New Room button is pressed
function newRoom() {
    //emptying the chat log for this client since they are joining a different room
    document.getElementById("log").value = "";
    //document.getElementById("game-status").innerText = "Game has not started.";


    // calling the ChatServlet to retrieve a new room ID
    let callURL = "http://localhost:8080/WSChatServer-1.0-SNAPSHOT/chat-servlet";
    fetch(callURL, {
        method: 'GET',
        headers: {
            'Accept': 'text/plain',
        },
    })
        .then(response => response.text())
        .then(response => enterRoom(response.trim()));
        //calling enterRoom() refreshes the list of rooms, no need to do it here
        // .then(response => {
        //     // Appends the new list item directly to the ul.nav
        //     document.querySelector(".nav").innerHTML += ` <li><button onclick="enterRoom('${response.trim()}')">${response.trim()}</button></li>`;
        // });
}

//Function called when the user presses one of the room buttons to enter it (is also called when creating a new room)
function enterRoom(code) {

    //alert the user then do nothing if they are already in this room
    if (code === currentRoomCode){
        alert("You are already in room " + code);
        return;
    }else{
        currentRoomCode = code;
    }

    userNamed = false;
    thisUsername = "";

    //delete the start game button if it exists (since we are entering a new room)
    if(document.getElementById("startGameButton") !== null){
        document.getElementById("startGameButton").remove();
    }


    document.getElementById("game-status").innerText = "Game has not started.";

    //reload an empty game board
    loadGameBoardSquares();

    // //if the game is not ongoing, set the game status in the webpage to "Game has not started."
    // if (!gameOngoing){
    //     document.getElementById("game-status").innerText = "Game has not started.";
    // }

    //emptying the chat log since they are joining a different room
    document.getElementById("log").innerHTML = "";

    // refresh the list of rooms
    refreshRooms();

    //leave previous room if client is in one.
    if(inRoom){ //if in room
        ws.close(); //close socket (leaving old chat room)

        //remove existing event listeners on "input", hoping this stops the blank chat messages
        //removeEventListener(type, listener)
        document.getElementById("input").removeEventListener("keyup", handleKeyInput);
         //remove existing event listeners on "status-input"
        document.getElementById("status-input").removeEventListener("keyup", sendStatusMessage);

    }

    inRoom = true;


    // create the web socket
    ws = new WebSocket("ws://localhost:8080/WSChatServer-1.0-SNAPSHOT/ws/" + code);


    // parse messages received from the server and update the UI accordingly
    ws.onmessage = function (event) {
        console.log("data from websocket: " + event.data);

        // parsing the server's message as json
        let message = JSON.parse(event.data);

        // handle message
        handleMessage(message);
    }

    //handleKeyInput is a function defined in this file. It's just what should happen if the user hits any key while in input
    document.getElementById("input").addEventListener("keyup", handleKeyInput);

    //sendStatusMessage is a function defined in this file. It's just what should happen if the user hits any key while on the status input
    document.getElementById("status-input").addEventListener("keyup", sendStatusMessage);

}

//this function just sees what type of message we are getting then calls the respective function to handle it
function handleMessage(message) {
    if (message.type === "roomUpdate") {
        updateCurrentRoom(message.message)
    } else if (message.type === "status") {
        displayUsersInRoom(message.message)
    } else if (message.type === "chat") {
        displayChat(message.username,message.message)
    } else if (message.type === "typing") {
        typingIndicator(message.message)
    } else if (message.type === "user-status") {
        updateStatusMessage(message.username,message.message,message)
    } else if (message.type === "image"){
        displayImage(message.username,message.message);
    } else if (message.type === "gameInit"){
        gameReady(message.message);
    } else if (message.type === "game-turn"){
        handleGameTurn(message.currentTurn);
    } else if (message.type === "game-pieces"){
        setGamePieces(message.black,message.white)
    } else if (message.type === "game-status"){
        updateGameStatus(message.move,message.player);
    } else if (message.type === "game-end"){
        endGame(message.winner);
    } else if(message.type === "clear-board"){
        clearBoard();
    }
    else {
        console.log("ERROR: Server sending message js cannot handle. Message: " + message.type);
    }
}

//we want to handle an update to the list of users in room, shown to client
function displayUsersInRoom(users){
    if (document.getElementById("users-list-message") !== null) {
        document.getElementById("users-list-message").remove(); //set it to empty so loop can just append values
    }
    document.getElementById("users-list").innerHTML = ""; //empties the list of users

    let userList = document.getElementById("users-list");


    const existingUsers = Array.from(userList.children).map(item => item.textContent);


    // //Removing any user who is no longer in the user list from the user list in the HTML (This would replace emptying the list
    //const userElements = [document.getElementById("users-list").querySelectorAll("li")];
    // for (const user of userElements) {
    //     console.log("User element:", user);
    //     if (!users.includes(user.textContent)) {
    //         console.log("Removing user:", user.textContent);
    //         userList.removeChild(user);
    //     }
    // }

    //appending any user who isn't already in the user list to the user list
    for(let i=0; i<users.length; i++){
        if (!existingUsers.includes(users[i])) {
            let tempItem = document.createElement("li");
            //tempItem.title = users[i];
            tempItem.textContent = users[i];
            userList.appendChild(tempItem);
        }
    }
}

//the message sent from the server should have "message" of "You are currently in room XZY"
//we just gotta put that in the correct element
function updateCurrentRoom(displayInfo) {
    document.getElementById("users-list").innerHTML = ""; //empties the list of users
    document.getElementById("current-room").innerText = displayInfo;
}

//outputs chat message to the log
function displayChat(username, message) {
    //creating a div for the message, this is where everything will be appended before appending the message div to the chat log
    let messageDiv = document.createElement('div');
    messageDiv.className = 'messageDiv';

    //this div will hold the username and the timestamp
    let senderDiv = document.createElement('div');
    senderDiv.className = 'senderDiv';

    let usernameSpan = document.createElement('span');
    usernameSpan.textContent = username;
    usernameSpan.className = 'usernames';

    let timestampSpan = document.createElement('span');
    timestampSpan.textContent = timestamp();
    timestampSpan.className = 'timestamps';

    senderDiv.appendChild(usernameSpan);
    senderDiv.appendChild(timestampSpan);

    let messageP = document.createElement('p');
    messageP.textContent = message;
    messageP.className = 'messages';

    messageDiv.appendChild(senderDiv);
    messageDiv.appendChild(messageP);

    //appending the message div to the chat log
    let log = document.getElementById("log");

    log.appendChild(messageDiv);

    messageDiv.scrollIntoView(); //keeps most recent messaged at bottom of log

}


//updates the typing indicator
function typingIndicator(message){
    if (document.getElementById("indicator").value === ""){

        document.getElementById("indicator").value = message;

        setTimeout(() => {
            document.getElementById("indicator").value = "";
        }, 2000);
    }
}

//this function is called when the refresh button is pressed, or when a new room is entered
function refreshRooms() {
    //refresh the list of rooms
    let callURL = "http://localhost:8080/WSChatServer-1.0-SNAPSHOT/refreshList";
    fetch(callURL, {
        method: 'GET',
        headers: {
            'Accept': 'application/json',
        },
    })
        .then(response => response.json())
        .then(response => updateRoomsList(response)); //just log to console for now
}

//takes an array of rooms
//adds all room  in array as li button elements in ul with class="nav"
//format of li: <li><button onclick="enterRoom('RoomID')">RoomID</button></li>
function updateRoomsList(rooms) {
    console.log(rooms.rooms)
    document.querySelector(".nav").innerHTML = "";
    for (let i=0;i<rooms.rooms.length; i++){
        document.querySelector(".nav").innerHTML += ` <li><button onclick="enterRoom('${rooms.rooms[i].trim()}')">${rooms.rooms[i].trim()}</button></li>`;
    }
}

//function to be called on event listener for key press
function handleKeyInput(event){
    //function for when the enter key is pressed so the user's message can be sent
    if (event.key === "Enter" && event.target.id === "input" && event.target.value !== "") {
        console.log("data sending to server: " + event.target.value);
        let request = {"type": "chat", "msg": event.target.value};
        ws.send(JSON.stringify(request));

        if (userNamed === false)
        {
            thisUsername = event.target.value;
            userNamed = true;
        }

        event.target.value = "";
    }
    //else statement for if any other key is pressed, so that the typing indicator is called
    else if (event.target.id === "input"){
        let msg = "a user is typing...";
        console.log("data sending to server: " + msg);
        let request = {"type": "typing", "msg": msg};
        ws.send(JSON.stringify(request));
    }
}

//timestamp function, gives the time at that specific hour and minute
function timestamp() {
    let d = new Date(), minutes = d.getMinutes();
    if (minutes < 10) minutes = '0' + minutes;
    return d.getHours() + ':' + minutes;
}

//function called when the send button is pressed
function sendButton(){
    //get the text from the input field
    let text = document.getElementById('input').value;

    //what to send if there is text
    if (text !== ""){
        //log it to console and send to server
        console.log("data sending to server: " +text);
        let request = {"type": "chat", "msg": text};
        ws.send(JSON.stringify(request));

        if (userNamed === false)
        {
            thisUsername = text;
            userNamed = true;
        }

        //reset the input field to empty
        document.getElementById('input').value = "";
    }

    //get the image from the input field
    let imageInput = document.getElementById('image-input');

    //send the image if there is an image and the user has already created their username
    if (imageInput.files.length > 0 && userNamed === true) {
        let file = imageInput.files[0];
        let reader = new FileReader();

        reader.onloadend = function() {
            let base64Image = reader.result;
            console.log("data sending to server: " + base64Image);
            let request = {"type": "image", "msg": base64Image};
            ws.send(JSON.stringify(request));
        }

        reader.readAsDataURL(file);

        document.getElementById('image-input').value = "";
    }

}

//sends the status message to the server
function sendStatusMessage(event){
    if (event.key === "Enter" && event.target.id === "status-input" && userNamed) {
        console.log("data sending to server: " + event.target.value);
        let request = {"type": "user-status", "msg": event.target.value};
        ws.send(JSON.stringify(request));
        event.target.value = "";
    }
}

//updates the status message for the client side
function updateStatusMessage(username, message){
    let userlist = document.getElementById("users-list").children;
    for (let i = 0; i < userlist.length; i++) {
        if(userlist[i].textContent == username){
            userlist[i].title = "Status: "+ message;
            displayChat("server", username + " has changed their status to '" + message + "'");
            break;
        }
    }
}

//function that adds the image to the chatlog div
function displayImage(username, message) {
    //creating an image element and setting all of its values
    let image = document.createElement('img');
    image.src = message;
    image.alt = "Image sent by '" + username + "' at " + timestamp();
    image.title = "Image sent by '" + username + "' at " + timestamp();

    //creating a div that will contain the sender, timestamp, and the image
    let messageDiv = document.createElement('div');
    messageDiv.className = 'messageDiv';

    let senderDiv = document.createElement('div');
    senderDiv.className = 'senderDiv';

    let usernameSpan = document.createElement('span');
    usernameSpan.textContent = username;
    usernameSpan.className = 'usernames';

    let timestampSpan = document.createElement('span');
    timestampSpan.textContent = timestamp();
    timestampSpan.className = 'timestamps';

    senderDiv.appendChild(usernameSpan);
    senderDiv.appendChild(timestampSpan);

    messageDiv.appendChild(senderDiv);
    messageDiv.appendChild(image);

    //appending the message div to the chat log
    let log = document.getElementById("log");
    log.appendChild(messageDiv);
    log.appendChild(document.createElement("br"));
}

//lets us not have to manually make a 15x15 board with unique ids for each square
function loadGameBoardSquares(){
    //clear the game board
    document.querySelector(".game-board").innerHTML = "";
    for(let row = 0; row < 15; row++){
        for(let col = 0; col < 15; col++){
            document.querySelector(".game-board").innerHTML += '<button class="square" id="' + row + "," + col + '" onclick="SquarePressed(' + row + "," + col + ')"></button>';
        }
    }
}

//function called when any square button is pressed
function SquarePressed(row,column){
    //console log the square that was pressed
    console.log("Square pressed: " + row + "," + column);

    //if the game is ongoing and the player is the one whose turn it is, send the move to the server
    if((playerTurn === thisUsername)){
        console.log("data sending to server: " + row + "," + column);
        let request = {"type": "updateGame", "msg": row + "," + column};
        ws.send(JSON.stringify(request));
    }
}
function alreadyPressed(){
    alert("This square has already been assigned.");
}

//function that is called when the server sends the gameInit message, indicating that a game is ready to start
function gameReady(message){
    if (document.getElementById("startGameButton") == null) {
        // Create a "Start Game" button for either of the players to press
        let startGameButton = document.createElement("button");
        startGameButton.textContent = "Start Game";
        startGameButton.id = "startGameButton";
        startGameButton.className = "new-room-btn";
        startGameButton.onclick = startGame;
        // Append the button to the room's div
        document.querySelector(".rooms").appendChild(startGameButton);
    }
}

//function called when start game button is pressed
function startGame(){
    //if a game is not ongoing
    if(gameOngoing===false) {
        //start the game
        gameOngoing = true;
        console.log("starting game");
        let request = {"type": "gameInit", "msg": "True"};
        ws.send(JSON.stringify(request));
    }

}

function clearBoard(){
    //delete all elements in game board
    document.getElementById("game-board").innerHTML="";

    //then reload empty board
    loadGameBoardSquares();
}

//function to update which player's turn it is, when this is called the game has started
function handleGameTurn(currentTurn){
    playerTurn = currentTurn;

    //delete the start game button if it exists (since the game has started)
    if(document.getElementById("startGameButton") !== null){
        document.getElementById("startGameButton").remove();
    }

    document.getElementById("game-status").innerText = "Current Turn: " + playerTurn;
    console.log("Current Turn: " + playerTurn);
}

//function that obtains the usernames for player one and two
function setGamePieces(black,white){
    playerTwo = white;
    playerOne = black;
    displayChat("server",playerOne + " vs " + playerTwo);
}



function updateGameStatus(square,player) {
    //updating the game as the moves are made by putting the pieces on the board then disabling the square
    if(player === playerTwo){
        document.getElementById(square).innerHTML += '<div class="whitePiece"></div>';
        document.getElementById(square).onclick = alreadyPressed;
        playClickSound();
    } else if (player === playerOne){
        document.getElementById(square).innerHTML += '<div class="blackPiece"></div>';
        document.getElementById(square).onclick = alreadyPressed;
        playClickSound();
    }
    else{
        console.log("ERROR: player not found");
    }
}
function playClickSound() {
    const clickSound = document.getElementById('clickSound');
    clickSound.currentTime = 0; // rewind to start
    clickSound.play();
}

function endGame(winner) {
    //ending the game and announcing the winner
    displayChat("server","Game Over. \nThe Winner is " + winner);
    //alert("Game Over. The Winner is " + winner);
    gameOngoing = false;
    document.getElementById("game-status").innerText = "Game Over \n The Winner is " + winner;

}


// Function to toggle dark mode
function toggleMode() {
    const modeToggleBtn = document.querySelector('.modeToggle');
    const currentMode = document.documentElement.getAttribute('data-mode');
    const newMode = currentMode === 'dark' ? 'light' : 'dark';

    document.documentElement.setAttribute('data-mode', newMode);

    if (newMode === 'dark') {
        modeToggleBtn.textContent = 'Light mode';
        setDarkMode();
    } else {
        modeToggleBtn.textContent = 'Dark mode';
        setLightMode();
    }

    // Store the current mode in localStorage
    localStorage.setItem('mode', newMode);
}

// Function to set dark mode
function setDarkMode() {
    document.documentElement.style.setProperty('--headerColor', '#1a1a1a');
    document.documentElement.style.setProperty('--footerColor', '#1a1a1a');
    document.documentElement.style.setProperty('--leftColor', '#333333');
    document.documentElement.style.setProperty('--rightColor', '#333333');
    document.documentElement.style.setProperty('--chatColor', '#444444');
    document.documentElement.style.setProperty('--buttonColor', '#555555');
    document.documentElement.style.setProperty('--textAreaColor', '#666666');
    document.documentElement.style.setProperty('--backdropColor', '#777777');
    document.documentElement.style.setProperty('--linkHoverColor', '#888888');
    document.documentElement.style.setProperty('--currentRoomFont', '#999999');
    document.documentElement.style.setProperty('--squareHover', '#adacac');
}

// Function to set light mode
function setLightMode() {
    document.documentElement.style.setProperty('--headerColor', '#264653');
    document.documentElement.style.setProperty('--footerColor', '#264653');
    document.documentElement.style.setProperty('--leftColor', '#E76F51');
    document.documentElement.style.setProperty('--rightColor', '#E76F51');
    document.documentElement.style.setProperty('--chatColor', '#E9C46A');
    document.documentElement.style.setProperty('--buttonColor', '#2A9D8F');
    document.documentElement.style.setProperty('--textAreaColor', 'wheat');
    document.documentElement.style.setProperty('--backdropColor', '#F4A261');
    document.documentElement.style.setProperty('--linkHoverColor', '#c7c7c7');
    document.documentElement.style.setProperty('--currentRoomFont', 'darkred');
    document.documentElement.style.setProperty('--squareHover', '#c4a67b');

}

// Check if mode is stored in localStorage
const storedMode = localStorage.getItem('mode');
if (storedMode) {
    // Set the mode to the stored mode
    document.documentElement.setAttribute('data-mode', storedMode);
    if (storedMode === 'dark') {
        setDarkMode();
        document.querySelector('.modeToggle').textContent = 'Light mode';
    } else {
        setLightMode();
        document.querySelector('.modeToggle').textContent = 'Dark mode';
    }
}


//This function runs when the js loads (ie when the page first loads)
(function () {
    //loadGameBoardSquares()
    refreshRooms();

    //refreshing the list of room every 5 seconds
    setInterval(refreshRooms, 5000);
})();


