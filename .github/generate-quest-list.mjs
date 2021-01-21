#!/usr/bin/env node

import {resolve, dirname, relative} from 'path';
import {readFile, readdir, writeFile} from 'fs/promises';

const scriptFilePath = new URL(import.meta.url).pathname;

const projectDirectory = resolve(dirname(scriptFilePath), '..');
const sourceDirectory = resolve(projectDirectory, 'app/src/main/java/de/westnordost/streetcomplete/');
const iconsDirectory = resolve(projectDirectory, 'res/graphics/quest icons/');

const markdownFilePath = resolve(projectDirectory, 'quests.md');

const noteQuestName = 'OsmNoteQuest';
const noteQuestPath = resolve(sourceDirectory, 'data/osmnotes/notequests/OsmNoteQuestType.kt');


/** @type string[] */
let questNames;

/** @type string[] */
let questFiles;

/** @type Record<string, string> */
let strings;


(async () => {
    const questFile = await readFile(resolve(sourceDirectory, 'quests/QuestModule.kt'), 'utf8');
    questNames = questFile.match(/(?<=^ {8})[A-Z][a-zA-Z]+(?=\()/gm);
    const sortedQuestNames = questNames.slice(0).sort();

    // sort note quest to the end
    questNames.unshift(noteQuestName);
    sortedQuestNames.push(noteQuestName);

    questFiles = await getFiles(resolve(sourceDirectory, 'quests/'));
    strings = await getStrings(resolve(projectDirectory, 'app/src/main/res/values/strings.xml'));

    const quests = await Promise.all(sortedQuestNames.map(name => getQuest(name)));

    await writeMarkdownFile(quests);
})().catch(error => {
    console.error(error);
    process.exit(1);
});


/**
 * @param {string} directory
 * @returns {Promise<string[]>} A list of all files (searched recursively) in the given directory.
 */
async function getFiles(directory) {
    const entries = await readdir(directory, { withFileTypes: true });

    return (await Promise.all(
        entries.map(entry => {
            const resolved = resolve(directory, entry.name);
            return entry.isDirectory() ? getFiles(resolved) : resolved;
        }),
    )).flat();
}


/**
 * @param {string} stringsFileName
 * @returns {Promise<Record<string, string>>} An object with all Android String Resource names and their respective values from the given file.
 */
async function getStrings(stringsFileName) {
    const stringRegex = /<string name="([^"]+)">(.*?)<\/string>/gs;

    const normalizeString = string =>
        string.replace(/^"/, '').replace(/"$/, '')  // strip optional quotes around the string
            .replace(/\\n/g, '\n')                  // replace \n with real newline characters
            .replace(/\\(['"])/g, '$1');            // unescape quotes

    const stringsContent = await readFile(stringsFileName, 'utf8');

    return Object.fromEntries(
        stringsContent.match(stringRegex).map(singleString => {
            const [, name, value] = new RegExp(stringRegex).exec(singleString);
            return [name, normalizeString(value)];
        }),
    );
}

/**
 * @typedef {object} Quest
 * @property {string} name - The quest's name
 * @property {string} icon - An absolute path to the quest's SVG icon.
 * @property {string} filePath - An absolute path to the quest's Kotlin file.
 * @property {string} title - The quest's title.
 * @property {number} defaultPriority - The quest's default priority (1 is highest priority).
 */


/**
 * @param {string} questName
 * @returns {Quest} All information about the quest with the given name.
 */
async function getQuest(questName) {
    const filePath = getQuestFilePath(questName);
    const questFileContent = await readFile(filePath, 'utf8');

    const titleStringName = getQuestTitleStringName(questName, questFileContent);

    return {
        name: questName,
        icon: await getQuestIcon(questName, questFileContent),
        filePath,
        title: strings[titleStringName].replace(/%s/g, '…'),
        defaultPriority: questNames.indexOf(questName) + 1,
    };
}


/**
 * @param {string} questName
 * @returns {string} The absolute path of the quest's file.
 */
function getQuestFilePath(questName) {
    if (questName === noteQuestName) {
        return noteQuestPath;
    }

    const questFile = questFiles.find(path => path.endsWith(questName + '.kt'));

    if (!questFile) {
        throw new Error(`Could not find quest file for quest '${questName}'.`);
    }

    return questFile;
}


/**
 * @param {string} questName
 * @param {string} questFileContent
 * @returns {Promise<string>} The absolute path of the quest's SVG icon.
 */
async function getQuestIcon(questName, questFileContent) {
    const [iconName] = questFileContent.match(/(?<=override val icon = R.drawable.ic_quest_)\w+/) ?? [];

    if (!iconName) {
        throw new Error(`Could not find the icon reference for quest '${questName}'.`);
    }

    const svgFileName = resolve(iconsDirectory, iconName + '.svg');

    try {
        await readFile(svgFileName);
        return svgFileName;
    }
    catch {
        throw new Error(`Could not find the SVG for icon '${iconName}' (quest '${questName}').`);
    }
}


/**
 * @param {string} questName
 * @param {string} questFileContent
 * @returns {string} The Android String Resource name of the quest's title.
 */
function getQuestTitleStringName(questName, questFileContent) {
    let stringResourceNames = questFileContent.match(/(?<=R\.string\.)quest_\w+/g);

    if (stringResourceNames.length === 0) {
        throw new Error(`Could not find the title string reference for quest '${questName}'.`);
    }

    if (stringResourceNames.length === 1) {
        return stringResourceNames[0];
    }

    // heuristic: use the last one that contains "title"
    return stringResourceNames.filter(name => name.includes('title')).pop();
}


/**
 * @param {Quest[]} quests
 */
async function writeMarkdownFile(quests) {
    const markdownFileDirectory = dirname(markdownFilePath);
    const scriptName = relative(projectDirectory, scriptFilePath);

    const markdownLines = [
        `<!-- this file is automatically generated by ${scriptName} -->`,
        '',
        '### StreetComplete quests',
        '',
        'See also the [quest list in the OSM Wiki](https://wiki.openstreetmap.org/wiki/StreetComplete/Quests).',
        'It is maintained by the OSM Wiki editors and thus may be a bit outdated, but it contains a lot more human-readable information.',
        '',
        '<table>',
        '  <thead>',
        '    <tr>',
        '      <th>Icon</th>',
        '      <th>Quest Name</th>',
        '      <th>Question</th>',
        '      <th>Default Priority</th>',
        '    </tr>',
        '  </thead>',
        '  <tbody>',

        ...quests.flatMap(quest => {
            const relativeIconPath = relative(markdownFileDirectory, quest.icon);
            const relativeFilePath = relative(markdownFileDirectory, quest.filePath);

            return [
                '    <tr>',
                `      <td><img src="${relativeIconPath}"></td>`,
                `      <td><a href="${relativeFilePath}">${quest.name}</a></td>`,
                `      <td>${quest.title}</td>`,
                `      <td>${quest.defaultPriority}</td>`,
                '    </tr>',
            ];
        }),

        '  </tbody>',
        '</table>',
    ];

    await writeFile(markdownFilePath, markdownLines.join('\n'));
}